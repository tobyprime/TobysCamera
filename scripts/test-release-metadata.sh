#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SCRIPT="$ROOT_DIR/scripts/release-metadata.sh"
readonly TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/release-metadata.XXXXXX")"
readonly REPOSITORY="$TEMP_DIR/repository"

cleanup() {
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local description="$3"

  [[ "$actual" == "$expected" ]] || fail "$description"
}

assert_fails() {
  local description="$1"
  shift

  if "$@" >/dev/null 2>&1; then
    fail "$description"
  fi
}

git init --quiet --initial-branch=main "$REPOSITORY"
git -C "$REPOSITORY" config user.email 'release-test@example.invalid'
git -C "$REPOSITORY" config user.name 'Release metadata test'

printf 'main\n' > "$REPOSITORY/file.txt"
git -C "$REPOSITORY" add file.txt
git -C "$REPOSITORY" commit --quiet -m 'main commit'
readonly MAIN_COMMIT="$(git -C "$REPOSITORY" rev-parse HEAD)"
git -C "$REPOSITORY" update-ref refs/remotes/origin/main "$MAIN_COMMIT"

git -C "$REPOSITORY" checkout --quiet -b feature
printf 'feature\n' >> "$REPOSITORY/file.txt"
git -C "$REPOSITORY" commit --quiet -am 'feature-only commit'
readonly FEATURE_COMMIT="$(git -C "$REPOSITORY" rev-parse HEAD)"

pushd "$REPOSITORY" >/dev/null

mod_output="$(GITHUB_SHA="$MAIN_COMMIT" "$SCRIPT" 'mod-v1.2.3')"
assert_equals $'kind=mod\nversion=1.2.3' "$mod_output" 'mod tag metadata should be emitted'

plugin_output="$(GITHUB_SHA="$MAIN_COMMIT" GITHUB_REF_NAME='plugin-v0.0.1' "$SCRIPT")"
assert_equals $'kind=plugin\nversion=0.0.1' "$plugin_output" 'plugin tag metadata should be emitted'

assert_fails 'invalid tag should fail' env GITHUB_SHA="$MAIN_COMMIT" "$SCRIPT" 'mod-v1.2'
assert_fails 'unknown tag kind should fail' env GITHUB_SHA="$MAIN_COMMIT" "$SCRIPT" 'server-v1.2.3'
assert_fails 'prerelease suffix should fail' env GITHUB_SHA="$MAIN_COMMIT" "$SCRIPT" 'mod-v1.2.3-rc.1'
assert_fails 'build suffix should fail' env GITHUB_SHA="$MAIN_COMMIT" "$SCRIPT" 'plugin-v1.2.3+build.1'
assert_fails 'feature-only commit should fail' env GITHUB_SHA="$FEATURE_COMMIT" "$SCRIPT" 'mod-v1.2.3'

github_output="$TEMP_DIR/github-output"
GITHUB_SHA="$MAIN_COMMIT" GITHUB_OUTPUT="$github_output" "$SCRIPT" 'mod-v1.2.3' >/dev/null
assert_equals $'kind=mod\nversion=1.2.3' "$(<"$github_output")" 'GitHub Actions output should be appended'

popd >/dev/null
printf 'release metadata tests passed\n'
