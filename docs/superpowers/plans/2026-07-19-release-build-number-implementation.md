# Release Build Number Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep release metadata at `X.Y.Z` while CI appends its run number only to JAR filenames and creates only normal GitHub Releases.

**Architecture:** The tag helper accepts only exact numeric Mod/plugin tags and outputs the base version. GitHub Actions derives `artifact_version` from that base plus `github.run_number`; Gradle uses `mod_version` for embedded metadata and `artifact_version` for output filenames.

**Tech Stack:** Bash, GitHub Actions, Gradle Kotlin DSL, Fabric Loom, Paperweight.

---

### Task 1: Constrain tag parsing to numeric release versions

**Files:**
- Modify: `scripts/release-metadata.sh`
- Modify: `scripts/test-release-metadata.sh`

- [ ] **Step 1: Write failing cases**

Add tests requiring `mod-v1.2.3` and `plugin-v0.0.1` to pass, while `mod-v1.2.3-rc.1` and `plugin-v1.2.3+build.1` fail.

- [ ] **Step 2: Run test to verify failure**

Run: `bash scripts/test-release-metadata.sh`

Expected: tests fail because the existing parser permits SemVer suffixes.

- [ ] **Step 3: Implement exact numeric parsing**

Make `scripts/release-metadata.sh` output only `kind` and numeric `version`; remove prerelease output and reject all suffixes.

- [ ] **Step 4: Run parser tests**

Run: `bash scripts/test-release-metadata.sh`

Expected: all valid and invalid tag cases pass.

### Task 2: Separate artifact naming from embedded Gradle version

**Files:**
- Modify: `build.gradle.kts`
- Modify: `fabric/versions/build.gradle.kts`
- Modify: `fabric/versions/build-modern.gradle.kts`
- Modify: `folia/build.gradle.kts`

- [ ] **Step 1: Add a failing artifact-name assertion**

Run `./gradlew verifyModules -Pmod_version=1.2.3 -Partifact_version=1.2.3+build.42`; observe that the current artifact verifier still expects names derived from `mod_version`.

- [ ] **Step 2: Use `artifact_version` only for filenames**

Default `artifact_version` to the project version. Change all JAR filename and root collection checks to use it, leaving resource expansion and embedded-version verification on `mod_version`.

- [ ] **Step 3: Verify metadata/name separation**

Run `./gradlew verifyModules -Pmod_version=1.2.3 -Partifact_version=1.2.3+build.42`.

Expected: JAR names include `+build.42`, but Fabric and plugin embedded versions remain `1.2.3`.

### Task 3: Derive build numbers and create normal Releases

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

- [ ] **Step 1: Set CI-only artifact version**

Pass `-Pmod_version=<metadata version>` and `-Partifact_version=<metadata version>+build.<github.run_number>` to Gradle.

- [ ] **Step 2: Remove prerelease publishing**

Always call `gh release create` without `--prerelease` and select staged artifacts by the derived artifact version.

- [ ] **Step 3: Update documentation**

Document exact numeric tag syntax, automatic `+build.<run_number>` filenames, numeric embedded metadata, and normal Release behavior.

- [ ] **Step 4: Commit and verify**

Run parser tests, Gradle verification with `1.2.3` and `1.2.3+build.42`, `git diff --check`, then commit all implementation and documentation files.
