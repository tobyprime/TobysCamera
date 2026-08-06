#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly GRADLE_WRAPPER="$ROOT_DIR/.gradlew-folia-test-wrapper.sh"

cleanup() {
  rm -f "$GRADLE_WRAPPER"
}
trap cleanup EXIT

sed 's/\r$//' "$ROOT_DIR/gradlew" > "$GRADLE_WRAPPER"
chmod +x "$GRADLE_WRAPPER"

readonly GRADLE=(bash "$GRADLE_WRAPPER")

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  local needle="$1"
  local haystack="$2"
  local description="$3"

  [[ "$haystack" == *"$needle"* ]] || fail "$description"
}

tasks_output="$(cd "$ROOT_DIR" && "${GRADLE[@]}" tasks --all --console=plain)"
assert_contains 'runFoliaServer' "$tasks_output" 'root Folia development task should exist'

dry_run_output="$(cd "$ROOT_DIR" && "${GRADLE[@]}" runFoliaServer --dry-run --console=plain)"
assert_contains ':folia:runFolia' "$dry_run_output" 'root Folia task should delegate to native runFolia'

printf 'Folia development server task tests passed\n'
