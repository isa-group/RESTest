#!/bin/sh
#
# Signs what dist/central.sh laid out for Maven Central and zips it into the bundle the Central
# Portal takes (ADR-0044):
#
#     SIGNING_KEY=<armoured secret key> SIGNING_PASSPHRASE=<its passphrase> \
#         .github/scripts/sign-for-central.sh <folder> <bundle.zip>
#
# writes an .asc signature beside every file but the checksums, which Central does not ask to be
# signed, checks each one, and zips the result, its paths starting at the group's first folder. The
# folder given is left as it was: what is signed is a copy. The key - exactly one - goes into a
# GnuPG home of this run's own, removed at the end with every process GnuPG starts for it, so nothing
# of it stays behind; the passphrase reaches GnuPG on its standard input, never on a command line.
# The release workflow runs it with RESTest's key, and a pull request with a key made for that run
# alone, to rehearse it.
#
# Plain sh, gpg and zip. Prints the key that signed and how many files it signed.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

fail() {
  echo "sign-for-central.sh: $*" >&2
  exit 1
}

usage="usage: SIGNING_KEY=... SIGNING_PASSPHRASE=... sign-for-central.sh <folder> <bundle.zip>"
folder=${1:?$usage}
bundle=${2:?$usage}
[ -n "${SIGNING_KEY:-}" ] || fail "SIGNING_KEY must hold the armoured secret key that signs RESTest's releases"
[ -n "${SIGNING_PASSPHRASE:-}" ] || fail "SIGNING_PASSPHRASE must hold that key's passphrase"

folder=$(cd "$folder" && pwd)
case $bundle in
  /*) ;;
  *) bundle=$PWD/$bundle ;;
esac

work=$(mktemp -d)
GNUPGHOME=$work/gnupg
export GNUPGHOME
mkdir -m 700 "$GNUPGHOME"
# Every process GnuPG started for this home - its agent, and the key box newer versions start - is
# stopped before the home is removed, on an interruption too.
cleanup() {
  gpgconf --kill all > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

# GnuPG's own messages say what it could not read, and hold nothing of the key.
printf '%s\n' "$SIGNING_KEY" | gpg --batch --quiet --import \
  || fail "SIGNING_KEY is not an armoured secret key GnuPG can import"
keys=$(gpg --batch --with-colons --list-secret-keys | grep -c '^sec:' || true)
[ "$keys" = 1 ] || fail "SIGNING_KEY must hold exactly one secret key, and holds $keys"
fingerprint=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')

cp -R "$folder" "$work/bundle"
find "$work/bundle" -type f ! -name '*.md5' ! -name '*.sha1' > "$work/files"
while IFS= read -r file; do
  printf '%s' "$SIGNING_PASSPHRASE" | gpg --batch --quiet --yes --pinentry-mode loopback \
    --passphrase-fd 0 --local-user "$fingerprint" --armor --detach-sign --output "$file.asc" "$file"
  gpg --batch --quiet --verify "$file.asc" "$file" 2> /dev/null \
    || fail "the signature of ${file#"$work/bundle/"} does not check out"
done < "$work/files"

rm -f "$bundle"
(cd "$work/bundle" && zip -qr "$bundle" .)
echo "Signed $(wc -l < "$work/files" | tr -d ' ') files with the key $fingerprint into $bundle"
