#!/bin/sh
#
# Assembles the folder a release of RESTest unpacks into, from a checkout where RESTest has been built:
#
#     ./mvnw -q package -DskipTests
#     dist/assemble.sh <output-directory> [--archives]
#
# leaves <output-directory>/restest-<version>/ with bin/ (the two launchers), lib/ (RESTest and every
# library it runs with), schemas/ (the JSON Schemas of a dictionary and a plan), LICENSE, NOTICE,
# THIRD-PARTY.txt (every library in lib/, with its licence) and README.md; with --archives, restest-<version>.zip and restest-<version>.tgz beside it, which are what
# a release carries. The container image runs it too, while it builds, so that the folder in the image
# and in the archives is the same one.
#
# Plain sh, and the tools every macOS and Linux has; zip only for --archives.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
out=${1:?usage: dist/assemble.sh <output-directory> [--archives]}
archives=${2:-}

version=$("$here/version.sh")
target=$root/restest-cli/target
jar=$target/restest-cli-$version.jar
if [ -z "$version" ] || [ ! -f "$jar" ] || [ ! -d "$target/lib" ]; then
  echo "dist/assemble.sh: RESTest ${version:-?} has not been built in this checkout. Run:" >&2
  echo "    ./mvnw -q package -DskipTests" >&2
  exit 1
fi

# Every library a release carries is listed, with its licence, in THIRD-PARTY.txt, and nothing else is:
# a library added, dropped or moved to another version stops the release here until its line is
# written, and its licence read.
listed=$(awk '$1 ~ /^lib\/.*\.jar$/ { print substr($1, 5) }' "$here/THIRD-PARTY.txt" | sort)
carried=$(cd "$target/lib" && ls -- *.jar | grep -v '^restest-' | sort)
if [ "$listed" != "$carried" ]; then
  echo "dist/assemble.sh: dist/THIRD-PARTY.txt does not list exactly the libraries RESTest runs with:" >&2
  for jar in $carried; do
    echo "$listed" | grep -qxF "$jar" || echo "    lib/$jar is carried but not listed" >&2
  done
  for jar in $listed; do
    echo "$carried" | grep -qxF "$jar" || echo "    lib/$jar is listed but not carried" >&2
  done
  exit 1
fi

name=restest-$version
mkdir -p "$out"
out=$(cd "$out" && pwd)
rm -rf "${out:?}/$name" "$out/$name.zip" "$out/$name.tgz"
mkdir -p "$out/$name/bin" "$out/$name/lib" "$out/$name/schemas"

# The launchers keep the line endings .gitattributes gives them: LF for sh, CRLF for cmd.exe.
cp "$here/bin/restest" "$here/bin/restest.cmd" "$out/$name/bin/"
cp "$jar" "$target"/lib/*.jar "$out/$name/lib/"
cp "$root"/schemas/*.schema.json "$out/$name/schemas/"
cp "$root/LICENSE" "$root/NOTICE" "$here/THIRD-PARTY.txt" "$root/README.md" "$out/$name/"

if [ "$archives" = --archives ]; then
  # COPYFILE_DISABLE keeps macOS's tar from adding a ._ file beside every file with extended
  # attributes; it means nothing to any other tar.
  (cd "$out" && COPYFILE_DISABLE=1 tar -czf "$name.tgz" "$name" && zip -qr "$name.zip" "$name")
  echo "$out/$name.zip"
  echo "$out/$name.tgz"
else
  echo "$out/$name"
fi
