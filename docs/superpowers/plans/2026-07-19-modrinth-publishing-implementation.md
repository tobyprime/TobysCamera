# Modrinth Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish each release JAR to one Modrinth project through Minotaur and synchronize a bilingual project body from CI-provided configuration.

**Architecture:** Root Minotaur configuration owns body synchronization and aggregate publish tasks; Fabric target projects and Folia own their artifact-specific Minotaur upload tasks. The tag workflow selects the aggregate task by artifact kind and passes only environment variables for token/project identity.

**Tech Stack:** Gradle Kotlin DSL, Minotaur 2.x, GitHub Actions, Bash.

---

### Task 1: Establish the canonical Modrinth body

**Files:**
- Create: `MODRINTH.md`
- Delete: `MODRINTH_en_US.md`
- Delete: `MODRINTH_zh_CN.md`
- Modify: `README.md`

- [ ] **Step 1: Build the combined body**

Copy the English document content into `MODRINTH.md`, append `---`, then copy the Chinese document content. Replace README links with a link to the new canonical file.

- [ ] **Step 2: Verify exact ordering**

Run: `rg -n '^---$|Modrinth-ready guides' MODRINTH.md README.md`

Expected: exactly one separator in the body and no links to removed language files.

### Task 2: Test and configure Minotaur Gradle tasks

**Files:**
- Modify: `build.gradle.kts`
- Modify: `fabric/versions/build.gradle.kts`
- Modify: `fabric/versions/build-modern.gradle.kts`
- Modify: `folia/build.gradle.kts`

- [ ] **Step 1: Write failing configuration checks**

Add a shell test that asserts `publishModrinthMod` and `publishModrinthPlugin` exist, select their expected project tasks, and fail with a clear missing-environment error before upload.

- [ ] **Step 2: Run the check to verify failure**

Run: `bash scripts/test-modrinth-publishing.sh`

Expected: failure because aggregate tasks and validation do not exist.

- [ ] **Step 3: Configure Minotaur**

Apply Minotaur 2.x to root, Fabric target projects, and Folia. Configure token/project ID from environment, `MODRINTH.md` body sync, artifact-filename version numbers, release version type, and accurate loaders/game versions. Add aggregate Mod/plugin tasks and missing-environment validation.

- [ ] **Step 4: Verify configuration without publishing**

Run: `bash scripts/test-modrinth-publishing.sh`

Expected: task selection passes and an upload command without environment values fails before network publication.

### Task 3: Connect tag CI to Modrinth

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Inject CI configuration**

Map `secrets.MODRINTH_TOKEN` to `MODRINTH_TOKEN` and `vars.MODRINTH_PROJECT_ID` to `MODRINTH_PROJECT_ID` at job scope.

- [ ] **Step 2: Publish the selected artifact kind**

After artifact selection, invoke `publishModrinthMod` for `mod` and `publishModrinthPlugin` for `plugin`. Do not expose token values in logs.

- [ ] **Step 3: Validate YAML and build configuration**

Run `./gradlew tasks --all`, the publishing test, and `./gradlew verifyModules` with an explicit artifact version. Inspect the workflow text to confirm only tag-kind selection controls publishing.

- [ ] **Step 4: Commit**

Commit the body migration, Gradle configuration, publishing test, CI workflow, and this plan.
