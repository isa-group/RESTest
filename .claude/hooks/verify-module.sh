#!/usr/bin/env bash
# Runs the tests of the Maven module that was just edited.
#
# Claude Code calls this after every Edit or Write and passes a JSON object on
# standard input describing the tool call. We pull the edited file's path out of
# it, work out which module it belongs to, and run that module's tests.
#
# Exit code 0  -> Claude carries on.
# Exit code 2  -> the output is fed back to Claude as something it must fix.
# Anything else is treated as a hook error and shown to the user.
#
# The point of this hook is that a broken build can never reach a pull request:
# the failure is caught seconds after the edit, by the person who made it.

set -uo pipefail

payload=$(cat)

# Extract .tool_input.file_path. Use jq when available, fall back to sed.
if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty')
else
  file_path=$(printf '%s' "$payload" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
fi

[ -n "${file_path:-}" ] || exit 0

# Only Java sources and POMs are worth rebuilding for.
case "$file_path" in
  *.java|*/pom.xml) ;;
  *) exit 0 ;;
esac

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$project_dir" || exit 0
[ -x ./mvnw ] || exit 0

# Walk up from the edited file until we find the directory holding a pom.xml.
dir=$(dirname "$file_path")
while [ "$dir" != "/" ] && [ "$dir" != "." ]; do
  if [ -f "$dir/pom.xml" ] && [ "$dir" != "$project_dir" ]; then
    module=$(basename "$dir")
    break
  fi
  dir=$(dirname "$dir")
done

[ -n "${module:-}" ] || exit 0

output=$(./mvnw -q -o -pl "$module" -am -DskipITs test 2>&1)
status=$?

if [ $status -ne 0 ]; then
  echo "Tests failed in module '$module' after editing $file_path." >&2
  echo "$output" | tail -n 60 >&2
  exit 2
fi

exit 0
