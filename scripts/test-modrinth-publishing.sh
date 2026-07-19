#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly GRADLE=(bash "$ROOT_DIR/gradlew")

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
assert_contains 'publishModrinthMod' "$tasks_output" 'Modrinth Mod aggregate task should exist'
assert_contains 'publishModrinthPlugin' "$tasks_output" 'Modrinth plugin aggregate task should exist'
assert_contains 'validateModrinthConfiguration' "$tasks_output" 'Modrinth configuration validation task should exist'

if (cd "$ROOT_DIR" && env -u MODRINTH_TOKEN -u MODRINTH_PROJECT_ID "${GRADLE[@]}" publishModrinthMod --console=plain) >"$ROOT_DIR/build/modrinth-missing-env.log" 2>&1; then
  fail 'Modrinth Mod publication should fail without environment configuration'
fi

missing_env_output="$(<"$ROOT_DIR/build/modrinth-missing-env.log")"
assert_contains 'MODRINTH_TOKEN' "$missing_env_output" 'Missing token error should name MODRINTH_TOKEN'
assert_contains 'MODRINTH_PROJECT_ID' "$missing_env_output" 'Missing project error should name MODRINTH_PROJECT_ID'

printf 'Modrinth publishing configuration tests passed\n'
