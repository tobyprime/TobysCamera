# Sharded Media Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store photo and video tile directories under a two-character UUID shard and migrate existing directories at repository startup.

**Architecture:** A package-private storage layout helper owns UUID-to-path mapping and legacy-directory migration. Both SQLite repositories use it for startup migration, writes, and reads.

**Tech Stack:** Java 21, `java.nio.file`, JUnit 5, SQLite JDBC.

---

### Task 1: Storage layout helper

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/ShardedMediaLayout.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/storage/ShardedMediaLayoutTest.java`

- [ ] Write tests for `ab...` becoming `root/ab/<uuid>` and for moving a direct legacy UUID directory.
- [ ] Run the test and verify it fails because `ShardedMediaLayout` is absent.
- [ ] Implement `directory(root, UUID)` and `migrateLegacyDirectories(root)` with atomic-move fallback and destination-conflict failure.
- [ ] Run the helper tests and verify they pass.

### Task 2: Repository integration

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqliteVideoRepository.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/storage/SqliteVideoRepositoryTest.java`

- [ ] Update persistence and reads to use `ShardedMediaLayout.directory`.
- [ ] Call migration after each media root is created and before the repositories are used.
- [ ] Assert new photo and video data are written to `<root>/<prefix>/<uuid>`.
- [ ] Run `./gradlew.bat :folia:test` and verify all storage tests pass.
