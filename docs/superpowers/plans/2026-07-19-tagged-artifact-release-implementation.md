# Tagged Artifact Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish Fabric Mod and Paper/Folia plugin artifacts independently from validated `main` tags.

**Architecture:** A Bash helper is the single boundary that parses tag names and ensures the tagged commit is reachable from `main`. The GitHub Actions workflow consumes its outputs, passes the derived version to Gradle, then uploads only the selected artifact kind to a GitHub Release.

**Tech Stack:** Bash, GitHub Actions, Gradle 9, Fabric Loom, Paperweight.

---

### Task 1: Test and implement release-tag metadata extraction

**Files:**
- Create: `scripts/test-release-metadata.sh`
- Create: `scripts/release-metadata.sh`

- [ ] **Step 1: Write a failing shell test**

Create `scripts/test-release-metadata.sh` with tests for `mod-v1.2.3`, `plugin-v1.2.3-rc.1`, invalid tags, and a commit outside `main`. Invoke `scripts/release-metadata.sh` from each test and assert its `kind`, `version`, and `prerelease` output.

- [ ] **Step 2: Run the test to verify it fails**

Run: `bash scripts/test-release-metadata.sh`

Expected: failure because `scripts/release-metadata.sh` does not yet exist.

- [ ] **Step 3: Implement the minimal metadata helper**

Create `scripts/release-metadata.sh`. It must accept a tag name as its first argument (or use `GITHUB_REF_NAME`), accept a commit from `GITHUB_SHA` (or `HEAD`), require that commit to be an ancestor of `${MAIN_REF:-origin/main}`, accept only `mod-v` or `plugin-v` prefixes followed by SemVer, and print `kind`, `version`, and `prerelease` as `key=value` lines.

- [ ] **Step 4: Run the helper test suite**

Run: `bash scripts/test-release-metadata.sh`

Expected: all release metadata checks pass.

### Task 2: Add the tag-triggered release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Add the workflow**

Create a workflow triggered only by `mod-v*` and `plugin-v*` tag pushes. Give it `contents: write`, perform a full checkout, install Temurin Java 25 with Gradle caching, run `scripts/release-metadata.sh "$GITHUB_REF_NAME"`, and expose its output through the step id `metadata`.

- [ ] **Step 2: Build and select artifacts**

Run `./gradlew verifyModules "-Pmod_version=${{ steps.metadata.outputs.version }}"`. For `mod`, collect exactly `build/libs/*/tobyscamera-<version>+mc*.jar`; for `plugin`, collect exactly `folia/build/libs/tobyscamera-plugin-<version>.jar`. Compute one `.sha256` file per collected JAR.

- [ ] **Step 3: Release selected artifacts**

Use `gh release create` with the pushed tag, selected JARs, and checksum files. Pass `--prerelease` only when the helper output says `prerelease=true`; use generated release notes.

### Task 3: Document and locally verify both artifact paths

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document release tags**

Add a release section stating that `mod-vX.Y.Z` publishes the two Fabric JARs and `plugin-vX.Y.Z` publishes the Paper/Folia plugin JAR, and that tags must be pushed from `main`.

- [ ] **Step 2: Run metadata tests and tag-versioned builds**

Run:

```bash
bash scripts/test-release-metadata.sh
./gradlew verifyModules "-Pmod_version=0.0.0-test.1"
```

Expected: both Fabric JARs and the plugin JAR include `0.0.0-test.1` in their filenames and embedded metadata.

- [ ] **Step 3: Commit the implementation**

```bash
git add .github/workflows/release.yml scripts/release-metadata.sh scripts/test-release-metadata.sh README.md docs/superpowers/plans/2026-07-19-tagged-artifact-release-implementation.md
git commit -m "ci: publish artifacts from release tags"
```

### Task 4: Verify the real GitHub release path

**Files:**
- No additional files.

- [ ] **Step 1: Fast-forward the reviewed release commit to `main` and push**

Run `git merge --ff-only codex/tag-release-ci` from the primary worktree, then `git push origin main`.

- [ ] **Step 2: Push a prerelease Mod test tag**

Run `git tag -a mod-v0.0.0-test.1 -m "Test Mod release"` on the pushed `main` commit, then `git push origin mod-v0.0.0-test.1`.

- [ ] **Step 3: Inspect the GitHub Actions run and GitHub Release**

Verify the release workflow succeeds and the `mod-v0.0.0-test.1` prerelease contains exactly two Fabric JARs and their SHA-256 checksum files.

- [ ] **Step 4: Push a prerelease plugin test tag and inspect its release**

Run `git tag -a plugin-v0.0.0-test.1 -m "Test plugin release"` on the pushed `main` commit, then `git push origin plugin-v0.0.0-test.1`. Verify its prerelease contains exactly the plugin JAR and its SHA-256 checksum file.
