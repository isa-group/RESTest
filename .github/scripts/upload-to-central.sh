#!/bin/sh
#
# Uploads a bundle sign-for-central.sh made to the Central Portal, as a deployment the Portal
# validates and does not publish, and waits until it has validated it (ADR-0044):
#
#     CENTRAL_USERNAME=<token username> CENTRAL_PASSWORD=<token password> \
#         .github/scripts/upload-to-central.sh <bundle.zip> <name>
#
# The release workflow's central job runs it on a tag; a maintainer runs it by hand for the trial
# upload docs/ci.md describes, so that the first tag runs nothing that has not run before. A person
# then publishes the deployment on the Portal, or drops it.
#
# The token is the Portal's user token, never a password. Nothing here prints a request's headers,
# and in GitHub Actions the token's base64 form, which GitHub does not know to hide, is masked before
# it is used. The upload is never repeated, which could leave two deployments; asking for its state
# is. VALIDATED is what is waited for, every ten seconds for half an hour; PUBLISHING and PUBLISHED
# mean somebody has published it in the meantime. When the Portal refuses the upload or the bundle,
# its own reasons are printed. In GitHub Actions the deployment's id and state also go to the step's
# outputs, as id and state.
#
# Plain sh, curl and jq.

set -eu

fail() {
  if [ "${GITHUB_ACTIONS:-}" = true ]; then
    echo "::error::$*"
  else
    echo "upload-to-central.sh: $*" >&2
  fi
  exit 1
}

usage="usage: CENTRAL_USERNAME=... CENTRAL_PASSWORD=... upload-to-central.sh <bundle.zip> <name>"
bundle=${1:?$usage}
name=${2:?$usage}
portal=${CENTRAL_PORTAL:-https://central.sonatype.com/api/v1/publisher}
[ -n "${CENTRAL_USERNAME:-}" ] && [ -n "${CENTRAL_PASSWORD:-}" ] \
  || fail "CENTRAL_USERNAME and CENTRAL_PASSWORD must hold the username and the password of a Central Portal user token"
[ -f "$bundle" ] || fail "$bundle is not a file"
# The name goes into the address as it is, its spaces written as %20.
printf '%s' "$name" | grep -Eqx '[A-Za-z0-9 ._-]+' \
  || fail "a deployment's name here is letters, digits, spaces, dots, dashes and underscores"
encoded=$(printf '%s' "$name" | sed 's/ /%20/g')

token=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')
if [ "${GITHUB_ACTIONS:-}" = true ]; then
  echo "::add-mask::$token"
fi

id=$(curl --fail-with-body -sS --max-time 600 -X POST -H "Authorization: Bearer $token" \
    -F "bundle=@$bundle" "$portal/upload?publishingType=USER_MANAGED&name=$encoded") \
  || fail "the Portal refused the upload: $id"
echo "$id" | grep -Eqx '[0-9a-f-]{36}' || fail "the Portal answered the upload with: $id"
echo "Uploaded as deployment $id, named $name"

state=UNKNOWN
answer=
validated=
for _ in $(seq 180); do
  sleep 10
  answer=$(curl --fail-with-body -sS --max-time 60 --retry 3 --retry-all-errors -X POST \
    -H "Authorization: Bearer $token" "$portal/status?id=$id") || continue
  state=$(printf '%s' "$answer" | jq -r .deploymentState 2> /dev/null) || continue
  case $state in
    VALIDATED | PUBLISHING | PUBLISHED) validated=yes; break ;;
    FAILED) fail "the Portal refused deployment $id: $(printf '%s' "$answer" | jq -c .errors)" ;;
  esac
done
[ -n "$validated" ] || fail "deployment $id is still $state after half an hour; the Portal last answered: $answer"

echo "Deployment $id is $state"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "id=$id" >> "$GITHUB_OUTPUT"
  echo "state=$state" >> "$GITHUB_OUTPUT"
fi
