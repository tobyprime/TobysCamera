# Folia Test Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent Folia 1.21.11 development server exposed through the root `runFoliaServer` Gradle task.

**Architecture:** Use `run-paper` 3.0.2's native `runPaper.folia.registerTask` API to create the module task `:folia:runFolia`. Configure that task with a separate `folia/folia-test-run` directory, local EULA/authentication settings, and port 25566. Add a root lifecycle alias while leaving the existing Paper `runServer` path unchanged.

**Tech Stack:** Gradle Kotlin DSL, `xyz.jpenilla.run-paper` 3.0.2, Folia/Minecraft 1.21.11, Java 21, Bash regression script.

---

### Task 1: Add the failing task-configuration regression test

**Files:**
- Create: `scripts/test-folia-development-server.sh`

- [ ] **Step 1: Write the failing test**

Create a shell test that checks the root task exists and resolves to the native Folia task:

```bash
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
assert_contains 'runFoliaServer' "$tasks_output" 'root Folia development task should exist'

dry_run_output="$(cd "$ROOT_DIR" && "${GRADLE[@]}" runFoliaServer --dry-run --console=plain)"
assert_contains ':folia:runFolia' "$dry_run_output" 'root Folia task should delegate to native runFolia'

printf 'Folia development server task tests passed\n'
```

- [ ] **Step 2: Run it to verify it fails for the expected reason**

Run `bash scripts/test-folia-development-server.sh`.

Expected: fail at the first assertion because `runFoliaServer` is not yet registered.

### Task 2: Configure the independent Folia Gradle task

**Files:**
- Modify: `folia/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `.gitignore`

- [ ] **Step 1: Register the native Folia task**

Add the following block to `folia/build.gradle.kts` after the existing Paper `tasks.runServer` configuration:

```kotlin
runPaper {
    folia {
        registerTask {
            minecraftVersion("1.21.11")
            runDirectory(file("folia-test-run"))
            doFirst {
                val directory = runDirectory.get().asFile
                directory.mkdirs()
                directory.resolve("eula.txt").writeText("eula=true\n")

                val propertiesFile = directory.resolve("server.properties")
                val properties = Properties()
                if (propertiesFile.isFile) {
                    propertiesFile.inputStream().use { input -> properties.load(input) }
                }
                properties.setProperty("online-mode", "false")
                properties.setProperty("server-port", "25566")
                propertiesFile.outputStream().use {
                    properties.store(it, "Local Folia development server configuration")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Register the root alias**

Add this task to `build.gradle.kts` beside the existing `runServer` lifecycle task:

```kotlin
tasks.register("runFoliaServer") {
    group = "application"
    description = "Starts a Folia 1.21.11 development server with the TobysCamera plugin."
    dependsOn(":folia:runFolia")
}
```

- [ ] **Step 3: Ignore generated Folia runtime state**

Add `folia/folia-test-run/` to `.gitignore` while preserving the existing `folia/run/` rule.

- [ ] **Step 4: Run the regression test to verify it passes**

Run `bash scripts/test-folia-development-server.sh`.

Expected: both task discovery and dry-run delegation assertions pass.

### Task 3: Document the second local server

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add the Folia command next to the Paper command**

Document `./gradlew runFoliaServer` and `./gradlew.bat runFoliaServer` as the local Folia 1.21.11 server command. State that it uses `folia/folia-test-run`, port 25566, `online-mode=false`, and the same plugin JAR as Paper.

- [ ] **Step 2: Verify documentation references the actual tasks**

Run `rg -n "runServer|runFoliaServer|Folia 1\.21\.11|25566" README.md` and confirm each documented command matches the Gradle configuration.

### Task 4: Verify the runtime and full test suite

**Files:**
- Generated and ignored: `folia/folia-test-run/`

- [ ] **Step 1: Run the Folia server**

Run `./gradlew.bat runFoliaServer --no-daemon`, allow startup to complete, and stop it cleanly with `stop`.

Expected: the log reports Folia 1.21.11, enables `TobysCamera`, and the generated properties contain `online-mode=false` and `server-port=25566`.

- [ ] **Step 2: Run the automated tests**

Run `./gradlew.bat test --no-daemon`.

Expected: `common`, `fabric`, and `folia` test suites pass.

- [ ] **Step 3: Check the final diff**

Run `git diff --check` and `git status --short`.

Expected: no whitespace errors; generated Folia runtime files remain ignored; unrelated existing worktree changes are preserved.

- [ ] **Step 4: Commit the implementation**

```bash
git add .gitignore README.md build.gradle.kts folia/build.gradle.kts scripts/test-folia-development-server.sh
git commit -m "build: add Folia development server task"
```
