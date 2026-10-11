#!/bin/sh
#
# Prints the version of RESTest this checkout declares, from the root pom.xml. The one place that
# reads it: the launcher of a checkout, dist/assemble.sh and the workflows all ask here.
#
# The carriage returns go first: Git on Windows may check pom.xml out with CRLF line endings.

set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
tr -d '\r' < "$root/pom.xml" | sed -n 's:^    <version>\(.*\)</version>$:\1:p' | head -n 1
