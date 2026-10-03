#!/bin/sh
#
# Builds the manual as one PDF, from the same Markdown files GitHub shows, with pandoc running in a
# container: nothing to install but Docker, and the same result on a laptop and on a build server.
#
#     docs/manual/pdf.sh [<ref>]
#
# Writes target/restest-manual.pdf. Links between chapters become links inside the PDF; links to the
# rest of the documentation go to the same file on GitHub, at <ref> - the commit checked out, unless
# a tag or a branch is named.

set -eu

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
ref=${1:-$(git -C "$root" rev-parse HEAD)}
version=$(git -C "$root" describe --tags --always "$ref" 2>/dev/null || echo "$ref")

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
  --metadata "date=$(date +%Y-%m-%d)" \
  --metadata "ref=$ref" \
  --lua-filter docs/manual/pdf/links.lua \
  --output target/restest-manual.pdf

echo "manual written to target/restest-manual.pdf"
