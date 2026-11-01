#!/bin/sh
#
# Prints the PATH with no Java on it, for checking a command that has to start RESTest with no Java
# installed: the PATH as it is, but for every folder holding a java or a javac. On macOS and Linux such
# a folder is replaced by one holding links to everything else in it - macOS keeps a java in /usr/bin,
# beside sh, bash and curl, and Ubuntu one that leads to the Java it has installed - and on Windows it
# is left out, since there it is a Java's own bin, or the javapath folder an Oracle installer adds,
# which hold nothing else a command needs.
#
#     .github/scripts/without-java.sh <a folder for the links, which it makes>
#
# A command that finds a Java some other way than through JAVA_HOME and the PATH - from a list of
# folders of its own - still finds it; jbang does not, which check-jbang.sh checks.
#
# Plain sh, run from Git Bash on Windows. The links stay in the folder given, for the caller to remove.

set -eu

# cd must go where it is told, not to a folder an exported CDPATH names.
unset CDPATH

usage="usage: without-java.sh <a folder for the links, which it makes>"
links=${1:?$usage}
mkdir "$links"

case $(uname -s) in
  MINGW* | MSYS* | CYGWIN*) windows=yes ;;
  *) windows=no ;;
esac

path=
replaced=0
# Read from a here-document rather than a pipe, so that path is still set once the loop ends; an empty
# entry, which means the current folder, is left out.
while IFS= read -r folder; do
  [ -n "$folder" ] || continue
  if [ -e "$folder/java" ] || [ -e "$folder/javac" ]; then
    [ "$windows" = no ] || continue
    replaced=$((replaced + 1))
    mkdir "$links/$replaced"
    # Gathered first and linked by one ln, since /usr/bin holds thousands of commands.
    set --
    for tool in "$folder"/*; do
      case ${tool##*/} in
        java | javac) ;;
        *) if [ -f "$tool" ] && [ -x "$tool" ]; then set -- "$@" "$tool"; fi ;;
      esac
    done
    [ $# -eq 0 ] || ln -s "$@" "$links/$replaced/"
    folder=$links/$replaced
  fi
  path=${path:+$path:}$folder
done <<FOLDERS
$(printf '%s\n' "$PATH" | tr ':' '\n')
FOLDERS

printf '%s\n' "$path"
