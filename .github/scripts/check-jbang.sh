#!/bin/sh
#
# Checks RESTest's jbang catalog from end to end: that jbang, on a machine with no Java, starts the
# RESTest the catalog names; that the command `jbang app install` makes of it runs on the jars of the
# release archive, byte for byte; and that this command passes check-restest.sh --own-java - on Windows
# as cmd.exe runs it, and as PowerShell does, which runs other scripts.
#
#     .github/scripts/check-jbang.sh <catalog> <version> <restest-<version>.tgz> [<Maven repository>]
#
# The catalog is a jbang-catalog.json that dist/jbang/catalog.sh wrote, or a published catalog's name
# as a person types it, isa-group. The Maven repository is a folder dist/central.sh laid out, for a
# version Maven Central does not have yet: jbang then takes RESTest's modules from it and every other
# library from Central, as it would once the version is published.
#
# What jbang writes goes into a folder of the check's own, removed when it ends: jbang itself, of the
# version below, checked against its SHA-256; the Javas it downloads (JBANG_DIR); and the libraries
# (JBANG_REPO, in place of ~/.m2/repository). The first `jbang app install` on a machine adds jbang's
# bin to the PATH in the shell's start-up files - on Windows, in the user's Path - and, where there is
# no Java, sets JAVA_HOME to jbang's own; it does neither when that bin is on the PATH already, as it is
# here. jbang writes those files in the home folder its Java is told of, which is a folder of the
# check's own too, and the check fails if it finds one written there, or, on Windows, if the user's
# Path or JAVA_HOME changed.
#
# Plain sh, run from Git Bash on Windows, where jbang is run as cmd.exe and PowerShell run it. Prints
# what `version` says on a machine with no Java, and the size of what jbang downloaded there.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: check-jbang.sh <catalog> <version> <restest-<version>.tgz> [<Maven repository>]"
catalog=${1:?$usage}
expected=${2:?$usage}
archive=${3:?$usage}
repository=${4:-}
here=$(cd "$(dirname "$0")" && pwd)

# The jbang every check runs, which a person moves on by hand: its version and the SHA-256 of
# jbang-<version>.tar, as https://github.com/jbangdev/jbang/releases gives it.
jbang_version=0.142.0
jbang_sha256=43aaeb9bc30d9331ed57952a872b80dab3dafe8e927f0f7a90a3c8e67d08eaac

case $(uname -s) in
  MINGW* | MSYS* | CYGWIN*) windows=yes ;;
  *) windows=no ;;
esac

fail() {
  echo "check-jbang: $*" >&2
  exit 1
}

ok() {
  echo "ok - $*"
}

# A path as jbang, a Java program, reads it: the Windows form on Windows.
native() {
  if [ "$windows" = yes ]; then cygpath -w "$1"; else printf '%s\n' "$1"; fi
}

# "<SHA-256>  <name>" for each file given, sorted by name: one process for them all, which matters in
# Git Bash, where starting one is slow, and whose sha256sum may write " *" before a name.
hashes() {
  if command -v sha256sum > /dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi \
    | sed 's|^\([0-9a-f]*\) [ *].*/|\1  |' | sort -k 2
}

[ -f "$archive" ] || fail "$archive is not a file"
[ -z "$repository" ] || [ -d "$repository" ] || fail "$repository is not a folder"
if [ -f "$catalog" ]; then
  catalog=$(native "$(cd "$(dirname "$catalog")" && pwd)/$(basename "$catalog")")
else
  case $catalog in
    *[!A-Za-z0-9-]* | '') fail "$catalog is neither a file nor the name of a catalog on GitHub" ;;
  esac
fi
alias=restest@$catalog

# Written the way Java writes it, with no //, which macOS's TMPDIR ending in / would leave: jbang looks
# for its bin on the PATH as text.
work=$(mktemp -d "${TMPDIR:-/tmp}/check-jbang.XXXXXX")
trap 'rm -rf "$work"' EXIT
work=$(cd "$work" && pwd -P)

# --- jbang, and a home of its own ---

curl -fsSL --retry 3 -o "$work/jbang.tar" \
  "https://github.com/jbangdev/jbang/releases/download/v$jbang_version/jbang-$jbang_version.tar"
[ "$(hashes "$work/jbang.tar" | cut -d ' ' -f 1)" = "$jbang_sha256" ] \
  || fail "jbang-$jbang_version.tar does not have the SHA-256 this script expects"
tar -xf "$work/jbang.tar" -C "$work"
jbang_bin=$work/jbang-$jbang_version/bin

mkdir -p "$work/jbang-dir/bin" "$work/repository" "$work/run" "$work/home"
[ -z "$repository" ] || cp -R "$repository/." "$work/repository/"
# Nothing of the person's own jbang settings, and no check for a newer jbang, whose notice would be
# noise here. user.home is read by jbang's own Java only, not by RESTest's.
for variable in $(env | sed -n 's/^\(JBANG_[A-Za-z0-9_]*\)=.*/\1/p'); do unset "$variable"; done
JBANG_DIR=$(native "$work/jbang-dir")
JBANG_REPO=$(native "$work/repository")
JBANG_NO_VERSION_CHECK=true
JBANG_JAVA_OPTIONS=-Duser.home=$(native "$work/home")
export JBANG_DIR JBANG_REPO JBANG_NO_VERSION_CHECK JBANG_JAVA_OPTIONS
PATH=$jbang_bin:$work/jbang-dir/bin:$PATH
export PATH

[ "$windows" = no ] || cmd_exe=$(command -v cmd.exe)

# jbang as a person runs it: its shell script on macOS and Linux, jbang.cmd from cmd.exe on Windows.
# Output in $work/stdout and $work/stderr.
jbang() {
  if [ "$windows" = yes ]; then
    (cd "$work/run" && "$cmd_exe" //c "$(cygpath -w "$jbang_bin/jbang.cmd")" "$@")
  else
    (cd "$work/run" && "$jbang_bin/jbang" "$@")
  fi > "$work/stdout" 2> "$work/stderr"
}

said() {
  echo "It wrote:"
  cat "$work/stdout" "$work/stderr"
}

first_line() {
  head -n 1 "$work/stdout" | tr -d '\r'
}

# What the first `jbang app install` changes on Windows, where it is not a file in the home folder.
user_settings() {
  if [ "$windows" = yes ]; then
    powershell.exe -NoProfile -NonInteractive -Command \
      "[Environment]::GetEnvironmentVariable('Path', 'User'); [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')"
  fi
}
settings_before=$(user_settings)

# --- From nothing: no Java on the machine, and a jbang that has never run ---

# jbang starts only on a JDK, so it downloads one for itself, and then one of 21 or later for RESTest,
# which its own is not. Two Javas in its folder afterwards say that both came from jbang, rather than
# from somewhere the PATH still reached.
without_java=$("$here/without-java.sh" "$work/without-java")
(
  unset JAVA_HOME
  PATH=$without_java
  jbang "$alias" version
) || fail "with no Java, 'jbang $alias version' answered $?. $(said)"
[ "$(first_line)" = "RESTest $expected" ] \
  || fail "with no Java, 'jbang $alias version' said '$(first_line)' rather than 'RESTest $expected'"
javas=$(ls "$work/jbang-dir/cache/jdks" 2> /dev/null | wc -l | tr -d ' ')
[ "$javas" -ge 2 ] \
  || fail "with no Java, jbang ran RESTest without downloading a Java for itself and one for RESTest"
ok "with no Java, jbang downloads a Java and starts RESTest $expected"
# For the documentation, which shows it: what a person with no Java sees, and what jbang downloaded.
tr -d '\r' < "$work/stdout" | sed 's/^/    /'
echo "    Javas: $(du -sk "$work/jbang-dir/cache/jdks" | cut -f 1) KB; libraries: $(du -sk "$work/repository" | cut -f 1) KB"

# --- The command jbang installs ---

jbang app install --name restest "$alias" || fail "'jbang app install' failed. $(said)"
command=$work/jbang-dir/bin/restest
[ "$windows" = no ] || command=$command.cmd
[ -f "$command" ] || fail "'jbang app install' made no $command. $(said)"
ok "jbang app install makes the command restest"

# The libraries jbang runs RESTest with, against the archive's lib/: the same files, with the same
# bytes, so that the catalog runs the RESTest the archive does.
jbang info classpath "$alias" || fail "'jbang info classpath' failed. $(said)"
classpath=$(tr -d '\r\n' < "$work/stdout")
[ "$windows" = no ] || classpath=$(cygpath -u -p "$classpath")
set --
while IFS= read -r jar; do
  [ -z "$jar" ] || set -- "$@" "$jar"
done <<JARS
$(printf '%s\n' "$classpath" | tr ':' '\n')
JARS
[ $# -gt 0 ] || fail "'jbang info classpath' printed no jar. $(said)"
hashes "$@" > "$work/jbang-jars"
mkdir "$work/archive"
tar -xzf "$archive" -C "$work/archive"
lib=$work/archive/restest-$expected/lib
[ -d "$lib" ] || fail "$archive holds no restest-$expected/lib"
hashes "$lib"/*.jar > "$work/archive-jars"
diff "$work/archive-jars" "$work/jbang-jars" > "$work/difference" \
  || fail "jbang runs RESTest on other libraries than the archive's lib/ (<: the archive's, >: jbang's):
$(cat "$work/difference")"
ok "jbang runs RESTest on the $(wc -l < "$work/archive-jars" | tr -d ' ') jars of the archive's lib/, byte for byte"

"$here/check-restest.sh" "$expected" --own-java "$command"

# PowerShell runs restest.ps1, and jbang.ps1 from it, rather than the .cmd ones.
[ "$windows" = no ] || "$here/check-restest.sh" "$expected" --own-java "$work/jbang-dir/bin/restest.ps1"

for file in .bashrc .bash_profile .zshrc .config/fish/conf.d/jbang.fish; do
  [ ! -e "$work/home/$file" ] || fail "jbang wrote ~/$file, which it does only when its bin is not on the PATH:
$(cat "$work/home/$file")"
done
[ "$(user_settings)" = "$settings_before" ] || fail "jbang changed the user's Path or JAVA_HOME"
ok "jbang changed no shell start-up file$([ "$windows" = no ] || echo ", and not the user's Path or JAVA_HOME")"
