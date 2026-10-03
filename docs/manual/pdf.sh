#!/bin/sh
#
# Builds the manual as one PDF, from the same Markdown files GitHub shows, with pandoc running in a
# container: nothing to install but Docker, and the same result on a laptop and on a build server.
#
#     docs/manual/pdf.sh [<ref>]
#
# Writes target/restest-manual.pdf from the files checked out. Links between chapters become links
# inside the PDF; links to the rest of the documentation go to the same file on GitHub, at <ref> -
# the commit checked out, unless a tag or a branch is named. <ref> also gives the PDF its version
# and its date: a version tag of 2.x when <ref> carries one, and otherwise the version the build
# declares and the commit; the date is the commit's, so the same commit always makes the same PDF.

set -eu

# Git Bash on Windows would otherwise rewrite the paths given to Docker below into Windows paths.
export MSYS_NO_PATHCONV=1

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
ref=${1:-$(git -C "$root" rev-parse HEAD)}
# Only 2.x tags: the tags of RESTest 1.x are in this history too, and would name a 1.x version.
version=$(git -C "$root" describe --tags --exact-match --match 'v2.*' "$ref" 2>/dev/null || true)
if [ -z "$version" ]; then
  declared=$(sed -n 's:^    <version>\(.*\)</version>$:\1:p' "$root/pom.xml" | head -1)
  version="$declared, commit $(git -C "$root" rev-parse --short "$ref")"
fi
date=$(git -C "$root" log -1 --format=%cs "$ref")

# pandoc with LaTeX and the Eisvogel template, at a version that does not move under the script.
image=pandoc/extra:3.11.0-debian

mkdir -p "$root/target"
cd "$root"
# The chapters in order; the contents page is GitHub's, and the PDF builds its own.
chapters=$(ls docs/manual/[0-9][0-9]-*.md)

# Run as the caller, so that the PDF belongs to whoever asked for it. HOME is somewhere that user
# can write, which TeX needs for its caches.
# shellcheck disable=SC2086
docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env HOME=/tmp \
  --volume "$root:/data" \
  --workdir /data \
  "$image" \
  $chapters \
  --from gfm \
  --pdf-engine xelatex \
  --template eisvogel \
  --top-level-division chapter \
  --number-sections \
  --toc \
  --metadata-file docs/manual/pdf/metadata.yaml \
  --metadata "subtitle=Version $version" \
  --metadata "date=$date" \
  --metadata "ref=$ref" \
  --lua-filter docs/manual/pdf/links.lua \
  --output target/restest-manual.pdf

echo "manual written to target/restest-manual.pdf"
