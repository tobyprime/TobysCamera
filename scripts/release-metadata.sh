#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'release metadata: %s\n' "$1" >&2
  exit 1
}

readonly TAG_NAME="${1:-${GITHUB_REF_NAME:-}}"
readonly COMMIT_REF="${GITHUB_SHA:-HEAD}"
readonly MAIN_REF="${MAIN_REF:-origin/main}"
readonly VERSION_PATTERN='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'

[[ -n "$TAG_NAME" ]] || fail 'missing release tag (pass it as the first argument or set GITHUB_REF_NAME)'

if [[ "$TAG_NAME" =~ ^(mod|plugin)-v($VERSION_PATTERN)$ ]]; then
  readonly KIND="${BASH_REMATCH[1]}"
  readonly VERSION="${BASH_REMATCH[2]}"
else
  fail "tag '$TAG_NAME' must match mod-vX.Y.Z or plugin-vX.Y.Z"
fi

git rev-parse --verify --quiet "${COMMIT_REF}^{commit}" >/dev/null \
  || fail "cannot resolve release commit '$COMMIT_REF'"
git rev-parse --verify --quiet "${MAIN_REF}^{commit}" >/dev/null \
  || fail "cannot resolve main ref '$MAIN_REF'"
git merge-base --is-ancestor "$COMMIT_REF" "$MAIN_REF" \
  || fail "release commit '$COMMIT_REF' is not reachable from '$MAIN_REF'"

emit() {
  printf 'kind=%s\nversion=%s\n' "$KIND" "$VERSION"
}

emit
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  emit >> "$GITHUB_OUTPUT"
fi
