#!/bin/sh
#
# Lays out what Maven Central gets of a release of RESTest, from a checkout where RESTest has been
# built with the central profile:
#
#     ./mvnw -q package -Pcentral -DskipTests -pl restest-cli -am
#     dist/central.sh <output-directory>
#
# leaves <output-directory>/maven-central/, a Maven repository holding restest-cli and every module it
# runs with: for each, its POM, its jar, its sources and its Javadoc, each with the .md5 and .sha1
# Central asks for. .github/scripts/sign-for-central.sh signs it and zips it, and the release
# workflow uploads that zip. ADR-0044 says why it is shaped this way.
#
# Each POM names every library Maven resolved for its module, each excluding whatever it would bring
# in turn. The script refuses unless every module names each library at the version restest-cli runs
# with, and unless Maven, fetching each module from the folder offline as any program depending on it
# would, gets the libraries its POM names - and, for restest-cli, exactly the jars in
# restest-cli/target/lib, the ones the release archive carries, byte for byte.
#
# Plain sh and the tools every macOS and Linux has, with the checkout's own ./mvnw for that check.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
out=${1:?usage: dist/central.sh <output-directory>}

fail() {
  echo "dist/central.sh: $1" >&2
  shift
  for line in "$@"; do
    echo "$line" >&2
  done
  exit 1
}

build="    ./mvnw -q package -Pcentral -DskipTests -pl restest-cli -am"
version=$("$here/version.sh")
cli=$root/restest-cli/target
for file in flattened-pom.xml runtime-dependencies.txt lib; do
  [ -e "$cli/$file" ] || fail "RESTest ${version:-?} has not been built in this checkout with -Pcentral. Run:" "$build"
done

# A version the root POM gives as a property of its own.
property() {
  tr -d '\r' < "$root/pom.xml" | sed -n "s:^        <$1>\(.*\)</$1>\$:\1:p"
}

# maven-dependency-plugin's list writes one library a line, after a heading, as
# group:artifact:type:version:scope or group:artifact:type:classifier:version:scope, followed by
# " -- module <name>" when the jar names its module. As group:artifact:version, sorted:
libraries() {
  tr -d '\r' < "$1" | awk '{ n = split($1, c, ":") } n == 5 || n == 6 { print c[1] ":" c[2] ":" c[n - 1] }' | sort
}

# The names maven-dependency-plugin gives the files of such a list when it copies them, sorted.
jars() {
  tr -d '\r' < "$1" | awk '{ n = split($1, c, ":") }
    n == 5 { print c[2] "-" c[4] "." c[3] }
    n == 6 { print c[2] "-" c[5] "-" c[4] "." c[3] }' | sort
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# restest-cli and the modules it runs with.
group=$(tr -d '\r' < "$cli/flattened-pom.xml" | sed -n 's:^  <groupId>\(.*\)</groupId>$:\1:p' | head -n 1)
libraries "$cli/runtime-dependencies.txt" > "$work/runs-with"
modules="restest-cli $(sed -n "s/^$group:\([^:]*\):.*/\1/p" "$work/runs-with")"

mkdir -p "$out"
out=$(cd "$out" && pwd)
repository=$out/maven-central
rm -rf "$repository"

# The module's flattened POM with its <dependencies> replaced by the list Maven resolved for it.
# flatten-maven-plugin writes the POM indented by two spaces, its closing tag alone on the last line.
write_pom() { # <libraries> <flattened POM> <output>
  awk '
    { sub(/\r$/, "") }
    FNR == NR {
      n = split($1, c, ":")
      if (n != 5 && n != 6) next
      kind = ""
      if (c[3] != "jar") kind = kind "      <type>" c[3] "</type>\n"
      if (n == 6) kind = kind "      <classifier>" c[4] "</classifier>\n"
      libraries = libraries "    <dependency>\n" \
        "      <groupId>" c[1] "</groupId>\n" \
        "      <artifactId>" c[2] "</artifactId>\n" \
        "      <version>" c[n - 1] "</version>\n" kind \
        "      <scope>" c[n] "</scope>\n" \
        "      <exclusions>\n" \
        "        <exclusion>\n" \
        "          <groupId>*</groupId>\n" \
        "          <artifactId>*</artifactId>\n" \
        "        </exclusion>\n" \
        "      </exclusions>\n" \
        "    </dependency>\n"
      next
    }
    /^  <dependencies>$/ { declared = 1; next }
    declared && /^  <\/dependencies>$/ { declared = 0; next }
    declared { next }
    /^<\/project>$/ && libraries != "" { printf "  <dependencies>\n%s  </dependencies>\n", libraries }
    { print }
  ' "$1" "$2" > "$3"
}

for module in $modules; do
  target=$root/$module/target
  for file in "$module-$version.jar" "$module-$version-sources.jar" "$module-$version-javadoc.jar" \
      flattened-pom.xml runtime-dependencies.txt; do
    [ -f "$target/$file" ] || fail "$module/target/$file has not been built. Run:" "$build"
  done
  # Maven passes a parent's name on to no module, and Central refuses a POM without one.
  grep -q '^  <name>' "$target/flattened-pom.xml" \
    || fail "$module/pom.xml gives the module no <name>, which Maven Central asks of every POM"
  # A module built on its own can settle a library at another version than restest-cli does - when a
  # test library of restest-cli's asks for a newer one, for instance. Its POM would then name a
  # version the archive does not carry; the fix is to pin it in the root POM.
  differs=$(libraries "$target/runtime-dependencies.txt" | comm -23 - "$work/runs-with")
  if [ -n "$differs" ]; then
    fail "$module runs with libraries restest-cli does not run with, at least not at these versions, so its POM would name them:" \
      "$(echo "$differs" | sed 's/^/    /')" \
      "Pin the one that brings the others in the root pom.xml's dependencyManagement, at the version restest-cli runs with."
  fi
  folder=$repository/$(echo "$group" | tr . /)/$module/$version
  mkdir -p "$folder"
  cp "$target/$module-$version.jar" "$target/$module-$version-sources.jar" \
    "$target/$module-$version-javadoc.jar" "$folder/"
  write_pom "$target/runtime-dependencies.txt" "$target/flattened-pom.xml" "$folder/$module-$version.pom"
done

# What a program that depends on a module gets: Maven fetches each module, in a project of its own,
# from a copy of the folder, and every other library from this machine's own local repository, where
# the build left them, without the network. Both plugins at the versions the root POM gives, which
# the build has fetched already.
local_repository=$("$root/mvnw" -q -B -N \
  "org.apache.maven.plugins:maven-help-plugin:$(property maven-help-plugin.version):evaluate" \
  -Dexpression=settings.localRepository -DforceStdout)
cp -R "$repository" "$work/repository"
mkdir "$work/uses"
for module in $modules; do
  mkdir "$work/uses/$module"
  cat > "$work/uses/$module/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>uses-$module</artifactId>
  <version>1</version>
  <dependencies>
    <dependency>
      <groupId>$group</groupId>
      <artifactId>$module</artifactId>
      <version>$version</version>
    </dependency>
  </dependencies>
</project>
EOF
done
{
  echo '<project xmlns="http://maven.apache.org/POM/4.0.0">'
  echo '  <modelVersion>4.0.0</modelVersion>'
  echo '  <groupId>example</groupId>'
  echo '  <artifactId>uses-restest</artifactId>'
  echo '  <version>1</version>'
  echo '  <packaging>pom</packaging>'
  echo '  <modules>'
  for module in $modules; do
    echo "    <module>$module</module>"
  done
  echo '  </modules>'
  echo '</project>'
} > "$work/uses/pom.xml"
"$root/mvnw" -q -B -o -f "$work/uses/pom.xml" \
    -Dmaven.repo.local="$work/repository" -Dmaven.repo.local.tail="$local_repository" \
    "org.apache.maven.plugins:maven-dependency-plugin:$(property maven-dependency-plugin.version):copy-dependencies" \
    -DincludeScope=runtime > "$work/fetch.log" 2>&1 \
  || { cat "$work/fetch.log" >&2; fail "Maven could not fetch RESTest $version from what Maven Central gets"; }

# A POM Maven cannot read brings its module alone, and Maven says so only in a warning.
for module in $modules; do
  { jars "$root/$module/target/runtime-dependencies.txt"; echo "$module-$version.jar"; } | sort > "$work/expected"
  (cd "$work/uses/$module/target/dependency" && ls -- *.jar) | sort > "$work/fetched"
  diff "$work/expected" "$work/fetched" > "$work/differences" \
    || fail "a program that depends on $module $version would not get the libraries its POM is written from:" \
      "$(sed -n 's/^< /    it would not get /p; s/^> /    it would get /p' "$work/differences")"
done
mkdir "$work/carried"
cp "$cli/lib/"*.jar "$cli/restest-cli-$version.jar" "$work/carried/"
diff -r "$work/carried" "$work/uses/restest-cli/target/dependency" > "$work/differences" \
  || fail "a program that depends on restest-cli $version would not get what the release archive carries:" \
    "$(sed "s:$work/carried:the archive:; s:$work/uses/restest-cli/target/dependency:Maven Central:" "$work/differences")"

# Checksums last, of every file but the checksums themselves.
if command -v md5sum > /dev/null; then md5=md5sum; else md5='md5 -r'; fi
if command -v sha1sum > /dev/null; then sha1=sha1sum; else sha1='shasum -a 1'; fi
find "$repository" -type f > "$work/files"
while IFS= read -r file; do
  $md5 "$file" | cut -d ' ' -f 1 > "$file.md5"
  $sha1 "$file" | cut -d ' ' -f 1 > "$file.sha1"
done < "$work/files"

echo "$repository"
