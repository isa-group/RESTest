#!/bin/sh
#
# Checks one way of starting RESTest from end to end: that it starts, says which version it is, and
# answers with RESTest's own exit codes, unchanged by whatever stands between the person and Java.
#
#     .github/scripts/check-restest.sh <version> --launcher <bin/restest, bin\restest.cmd, or a command>
#     .github/scripts/check-restest.sh <version> --image <image>
#     .github/scripts/check-restest.sh <version> --checkout <the ./restest of a checkout>
#
# --launcher is a release's own launcher, or anything that installs RESTest and puts a command on the
# PATH; --image is a container image; --checkout is the shortcut at the root of a checkout, which is
# checked for less, since it is not how RESTest is installed. Every one of them is checked for:
#
#   - `version` answering 0, its first line `RESTest <version>`, also with JAVA_TOOL_OPTIONS set;
#   - an option that does not exist answering 2;
#   - a run against an address where nothing listens answering 3, and leaving report.json, of that
#     version, and run.sqlite in the directory it was started from.
#
# A launcher and an image are also checked for answering 127 when there is no Java they can use - none
# at all, or a JAVA_HOME with none in it, and a Java older than 21 when OLD_JAVA_HOME names one. A
# launcher on macOS or Linux is also started through a symbolic link. An image, and a launcher in a
# release's folder, are also checked for carrying the JSON Schemas of a dictionary and a plan. An image
# is also checked for being stopped the way `docker stop` stops it: 143, and a report that says the run
# was cut short.
#
# Plain sh, run from Git Bash on Windows. Leaves nothing behind.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: check-restest.sh <version> --launcher|--image|--checkout <what>"
expected=${1:?$usage}
mode=${2:?$usage}
subject=${3:?$usage}

case $(uname -s) in
  MINGW* | MSYS* | CYGWIN*) windows=yes ;;
  *) windows=no ;;
esac

work=$(mktemp -d "${TMPDIR:-/tmp}/check-restest.XXXXXX")
containers=
cleanup() {
  for container in $containers; do docker rm -f "$container" >/dev/null 2>&1 || true; done
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "check-restest: $*" >&2
  exit 1
}

ok() {
  echo "ok - $*"
}

case $mode in
  --launcher | --checkout)
    # An absolute path, so that it still names the same file once the checks move into $work.
    case $subject in
      */* | *\\*) subject=$(cd "$(dirname "$subject")" && pwd)/$(basename "$subject") ;;
      *) subject=$(command -v "$subject") || fail "no $subject on the PATH" ;;
    esac
    [ -f "$subject" ] || fail "$subject is not a file"
    ;;
  --image) ;;
  *) fail "$usage" ;;
esac

# A document with one operation, written here so that the check needs nothing from a checkout.
cat > "$work/openapi.yaml" <<'DOCUMENT'
openapi: 3.0.3
info:
  title: A check of how RESTest starts
  version: '1'
paths:
  /things:
    get:
      responses:
        '200':
          description: The things
DOCUMENT

# Runs RESTest the way being checked, from $work, with the environment given before `--` changed:
#     restest NAME=value ... -- argument ...
# NAME= takes NAME out of the environment, except in a container, where it can only be emptied. The
# values hold no spaces. Its output is in $work/stdout and $work/stderr, and its exit code in $status.
restest() {
  docker_env=
  env_options=
  while [ "$1" != -- ]; do
    docker_env="$docker_env -e $1"
    case $1 in
      *=) env_options="$env_options -u ${1%=}" ;;
      *) env_options="$env_options $1" ;;
    esac
    shift
  done
  shift
  status=0
  case $mode in
    --image)
      # shellcheck disable=SC2086
      docker run --rm --user "$(id -u):$(id -g)" -v "$work:/work" $docker_env "$subject" "$@" \
        > "$work/stdout" 2> "$work/stderr" || status=$?
      ;;
    *)
      case $subject in
        *.cmd) set -- "$(command -v cmd.exe)" //c "$(cygpath -w "$subject")" "$@" ;;
        *) set -- "$subject" "$@" ;;
      esac
      # shellcheck disable=SC2086
      (cd "$work" && env $env_options "$@") > "$work/stdout" 2> "$work/stderr" || status=$?
      ;;
  esac
}

# The first line it printed. Java on Windows ends its lines with a carriage return as well.
first_line() {
  head -n 1 "$work/stdout" | tr -d '\r'
}

said() {
  echo "It wrote:"
  cat "$work/stdout" "$work/stderr"
}

expect_status() {
  [ "$status" -eq "$1" ] || fail "$2 answered $status rather than $1. $(said)"
}

# --- What every way of starting RESTest is checked for ---

restest -- version
expect_status 0 "'version'"
[ "$(first_line)" = "RESTest $expected" ] \
  || fail "'version' said '$(first_line)' rather than 'RESTest $expected'"
ok "version says RESTest $expected"

restest JAVA_TOOL_OPTIONS=-Xmx256m -- version
expect_status 0 "'version' with JAVA_TOOL_OPTIONS set"
[ "$(first_line)" = "RESTest $expected" ] \
  || fail "'version' with JAVA_TOOL_OPTIONS set said '$(first_line)'"
ok "version answers the same with JAVA_TOOL_OPTIONS set"

restest -- --no-such-option
expect_status 2 "an option that does not exist"
ok "an option that does not exist answers 2"

restest -- run openapi.yaml --url http://127.0.0.1:1 --budget 3s --store
expect_status 3 "a run against an address where nothing listens"
[ -s "$work/restest-out/report.json" ] || fail "the run left no restest-out/report.json. $(said)"
[ -s "$work/restest-out/run.sqlite" ] || fail "the run left no restest-out/run.sqlite. $(said)"
grep -q "\"version\" *: *\"$expected\"" "$work/restest-out/report.json" \
  || fail "report.json does not say it was written by RESTest $expected"
ok "a run where nothing answers ends with 3 and leaves report.json and run.sqlite"

[ "$mode" = --checkout ] && exit 0

# --- What a launcher and an image are also checked for ---

# The JSON Schemas travel with RESTest, so that whoever writes a dictionary or a plan finds them beside
# the tool: in the image, and in a release's folder, known by this version's jar in the lib/ beside the
# launcher's bin/. A command some installer puts on the PATH may live elsewhere, beside a lib/ of
# other programs', and is not asked for them.
case $mode in
  --image)
    docker run --rm --entrypoint ls "$subject" /opt/restest/schemas/dictionary.schema.json \
      /opt/restest/schemas/campaign.schema.json > /dev/null 2>&1 \
      || fail "the image has no /opt/restest/schemas/ holding both JSON Schemas"
    ok "the image carries the JSON Schemas in /opt/restest/schemas/"
    ;;
  *)
    release=$(cd "$(dirname "$subject")/.." && pwd)
    if [ -f "$release/lib/restest-cli-$expected.jar" ]; then
      for schema in dictionary campaign; do
        [ -f "$release/schemas/$schema.schema.json" ] \
          || fail "the release's folder has no schemas/$schema.schema.json"
      done
      ok "the release's folder carries the JSON Schemas in schemas/"
    fi
    ;;
esac

case $mode in
  --image)
    # The image's Java is in /opt/java/openjdk and found only through JAVA_HOME and the PATH. An empty
    # JAVA_HOME is how a container unsets one.
    restest JAVA_HOME= PATH=/usr/sbin:/usr/bin:/sbin:/bin -- version
    ;;
  *)
    if [ "$windows" = yes ]; then
      # Git's own tools, and Windows' own folder, hold no Java.
      case $subject in
        *.cmd) path=/c/Windows/System32 ;;
        *) path=/usr/bin ;;
      esac
    else
      # Not the system's folders with Java taken out: macOS keeps a java in /usr/bin that finds any Java
      # installed, and Ubuntu may keep one there too. A folder with links to only the tools the launcher
      # uses instead.
      path=$work/tools
      mkdir "$path"
      for tool in dirname readlink sed head uname; do ln -s "$(command -v "$tool")" "$path/$tool"; done
    fi
    restest JAVA_HOME= PATH="$path" -- version
    ;;
esac
expect_status 127 "'version' with no Java"
ok "with no Java it answers 127"

no_java_here=$work/no-java-here
[ "$windows" = yes ] && no_java_here=$(cygpath -w "$no_java_here")
[ "$mode" = --image ] && no_java_here=/no-java-here
restest JAVA_HOME="$no_java_here" -- version
expect_status 127 "'version' with a JAVA_HOME that holds no Java"
ok "with a JAVA_HOME that holds no Java it answers 127"

if [ -n "${OLD_JAVA_HOME:-}" ]; then
  restest JAVA_HOME="$OLD_JAVA_HOME" -- version
  expect_status 127 "'version' on the Java in $OLD_JAVA_HOME"
  ok "on a Java older than 21 it answers 127"
fi

if [ "$mode" = --launcher ] && [ "$windows" = no ]; then
  mkdir "$work/elsewhere"
  ln -s "$subject" "$work/elsewhere/restest"
  subject=$work/elsewhere/restest
  restest -- version
  expect_status 0 "'version' through a symbolic link"
  ok "it starts through a symbolic link from another folder"
fi

[ "$mode" = --image ] || exit 0

# --- What only an image is checked for ---

# `docker stop` reaches Java only if Java is the container's own process. The run has to be busy when
# it is stopped, so it is pointed at something that answers - a web server in a container beside it,
# whose every answer is 404. Against an address where nothing listens, it would have ended already.
api=$(docker run -d --quiet \
  busybox:1.37@sha256:bdf57e528e45e4433820e045b29b4597825a1c9e38353532d90a01445013f82e httpd -f -p 8080)
containers="$containers $api"
run=$(docker run -d --user "$(id -u):$(id -g)" -v "$work:/work" --network "container:$api" "$subject" \
  run openapi.yaml --url http://localhost:8080 --budget 60s --out stopped)
containers="$containers $run"
sleep 5
docker stop "$run" > /dev/null
status=$(docker wait "$run")
[ "$status" -eq 143 ] || fail "'docker stop' made it answer $status rather than 143. $(docker logs "$run" 2>&1)"
grep -q '"cutShort" *: *true' "$work/stopped/report.json" \
  || fail "the report of a run stopped by 'docker stop' does not say it was cut short"
ok "docker stop ends a run with 143 and a report that says it was cut short"
