#!/bin/sh
#
# Prints RESTest's jbang catalog for one version: dist/jbang/jbang-catalog.json with the version of
# RESTest on Maven Central filled in.
#
#     dist/jbang/catalog.sh <version>
#
# The version is a release's when the catalog goes to isa-group/jbang-catalog, and a snapshot when a
# build checks what it has just built, from a Maven repository of its own.
#
# Plain sh, and the tools every macOS and Linux has.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: dist/jbang/catalog.sh <version>"
version=${1:?$usage}

# It lands between double quotes in JSON and in a sed replacement, where | and & mean something: a
# version made only of these characters can do neither. The case looks at the whole value, newlines
# included, where grep would look at each of its lines.
case $version in
  *[!0-9A-Z.-]*) false ;;
  *) printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$' ;;
esac || {
  echo "dist/jbang/catalog.sh: $version is not a version of RESTest such as 2.5.0" >&2
  exit 2
}

sed -e "s|@VERSION@|$version|" "$(dirname "$0")/jbang-catalog.json"
