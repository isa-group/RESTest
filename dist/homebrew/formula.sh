#!/bin/sh
#
# Prints RESTest's Homebrew formula for one release archive: dist/homebrew/restest.rb with the
# address of a restest-<version>.tgz and its SHA-256 filled in. Homebrew reads the version from the
# address.
#
#     dist/homebrew/formula.sh <address of restest-<version>.tgz> <its SHA-256>
#
# The address is the release's on GitHub when the formula goes to the tap, and a file:// one when a
# build checks the archive it has just assembled.
#
# Plain sh, and the tools every macOS and Linux has.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: dist/homebrew/formula.sh <address of restest-<version>.tgz> <its SHA-256>"
url=${1:?$usage}
sha256=${2:?$usage}

# Both land between double quotes in Ruby, where #{...} would run, and in a sed replacement, where |
# and & mean something: an address made only of the characters below can do neither.
if ! printf '%s\n' "$url" | grep -Eq '^(https|file)://[A-Za-z0-9._~:/%+-]+/restest-[0-9][A-Za-z0-9.-]*\.tgz$'; then
  echo "dist/homebrew/formula.sh: $url is not the address of a restest-<version>.tgz" >&2
  exit 2
fi
if ! printf '%s\n' "$sha256" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "dist/homebrew/formula.sh: $sha256 is not a SHA-256" >&2
  exit 2
fi

sed -e "s|@URL@|$url|" -e "s|@SHA256@|$sha256|" "$(dirname "$0")/restest.rb"
