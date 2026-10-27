#!/bin/sh
#
# Checks a Homebrew formula of RESTest from end to end: that it is written the way Homebrew asks, that
# Homebrew installs it, that the formula's own test passes, that the folder it installs is the archive
# as it is, and that the `restest` it puts on the PATH passes check-restest.sh on the Java it brought.
#
#     .github/scripts/check-homebrew.sh <formula.rb> <version> [--online]
#
# The formula is one dist/homebrew/formula.sh wrote. Homebrew installs a formula only from a tap, so
# the check makes a local one, isa-group/restest-check, a folder among Homebrew's taps, and removes it,
# and the RESTest it installed, when it ends. It refuses to start where RESTest is installed with
# Homebrew already, so that it never removes somebody's own. What else it leaves on a machine: the Java
# the formula needs, and what that Java needs, which may have been there before; Homebrew itself,
# updated as it updates itself before any install; and the Ruby gems `brew test` and `brew style`
# install into Homebrew's own folder. `brew test`, `brew style` and `brew audit` also turn Homebrew's
# developer mode on for good, so it is turned off again where it was off.
#
# --online checks the formula with `brew audit --strict --online`, which reads its address and its
# project's page on GitHub, rather than with `brew style` alone; a formula with a file:// address
# cannot pass it.
#
# Plain sh. Prints what `restest version` says, and the size of what Homebrew installed.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: check-homebrew.sh <formula.rb> <version> [--online]"
formula=${1:?$usage}
expected=${2:?$usage}
online=${3:-}
here=$(cd "$(dirname "$0")" && pwd)
tap=isa-group/restest-check

fail() {
  echo "check-homebrew: $*" >&2
  exit 1
}

[ -f "$formula" ] || fail "$formula is not a file"

# GitHub's Linux machines carry Homebrew without putting it on the PATH.
if ! command -v brew > /dev/null 2>&1 && [ -x /home/linuxbrew/.linuxbrew/bin/brew ]; then
  eval "$(/home/linuxbrew/.linuxbrew/bin/brew shellenv)"
fi
command -v brew > /dev/null 2>&1 || fail "there is no brew on the PATH"

# The download stays in Homebrew's cache, where the archive is compared with what was installed, and
# formulae that depend on one this install upgrades are left as they are. Homebrew still updates
# itself before installing, as it would for anybody: one far behind fails to install what its formulae
# now name.
export HOMEBREW_NO_INSTALL_CLEANUP=1 HOMEBREW_NO_INSTALLED_DEPENDENTS_CHECK=1 HOMEBREW_NO_ENV_HINTS=1
tap_folder=$(brew --repository "$tap")
developer=$(brew developer | head -n 1)

# A tap an earlier run left behind goes; a RESTest installed from it stays, since nothing here can tell
# it from somebody's own, and stops the check below.
rm -rf "$tap_folder"
if brew list --formula restest > /dev/null 2>&1; then
  fail "RESTest is installed with Homebrew here already. Run this where it is not, or 'brew uninstall restest' first if that one is not wanted."
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/check-homebrew.XXXXXX")
# Without removing, as Homebrew otherwise does, every formula on the machine that nothing needs.
# Uninstalling also takes back the trust installing gave it; an install that failed before it put
# anything in place leaves that trust, which is taken back here.
cleanup() {
  HOMEBREW_NO_AUTOREMOVE=1 brew uninstall --formula "$tap/restest" > /dev/null 2>&1 || true
  brew untrust --formula "$tap/restest" > /dev/null 2>&1 || true
  rm -rf "$tap_folder" "$work"
  case $developer in
    *disabled*) brew developer off > /dev/null 2>&1 || true ;;
  esac
}
trap cleanup EXIT

mkdir -p "$tap_folder/Formula"
cp "$formula" "$tap_folder/Formula/restest.rb"

# Installed by its full name, which is also what makes Homebrew trust a formula from a tap of nobody's
# it knows: style and audit refuse one it does not trust.
brew install --formula "$tap/restest"
brew test "$tap/restest"
if [ "$online" = --online ]; then
  brew audit --strict --online "$tap/restest"
else
  brew style "$tap/restest"
fi

# Every file of the archive but the Windows launcher is in libexec, where the formula put it, or at the
# top of what Homebrew installed, where it moves a licence, a notice and a README for every formula;
# and libexec holds nothing else.
keg=$(brew --prefix "$tap/restest")
tar -tzf "$(brew --cache --formula "$tap/restest")" | sed -e 's,^[^/]*/,,' -e '/\/$/d' -e '/^$/d' \
  | grep -vx 'bin/restest.cmd' | LC_ALL=C sort > "$work/archived"
(cd "$keg/libexec" && find . ! -type d | sed 's,^\./,,' | LC_ALL=C sort) > "$work/libexec"
(cd "$keg" && find . -maxdepth 1 -type f | sed 's,^\./,,'; cat "$work/libexec") | LC_ALL=C sort > "$work/installed"
missing=$(LC_ALL=C comm -23 "$work/archived" "$work/installed")
[ -z "$missing" ] || fail "Homebrew did not install these files of the archive: $missing"
added=$(LC_ALL=C comm -13 "$work/archived" "$work/libexec")
[ -z "$added" ] || fail "libexec holds files the archive does not: $added"
echo "ok - Homebrew installed the archive as it is"

"$here/check-restest.sh" "$expected" --own-java "$(brew --prefix)/bin/restest"

# What chapter 2 of the manual shows, from Homebrew's own installation.
(cd "$work" && "$(brew --prefix)/bin/restest" version)

# What a machine with none of it would download and keep.
cellar=$(brew --cellar)
needs=$(brew deps --formula "$tap/restest")
size=$(for name in restest $needs; do du -sk "$cellar/$name" 2> /dev/null; done \
  | awk '{ total += $1 } END { printf "%d", total / 1024 }')
echo "Installed: RESTest and the $(echo "$needs" | wc -w | tr -d ' ') formulae it needs, $size MB in all."
echo "Those formulae stay; 'brew autoremove' removes them, with any other formula nothing needs."
