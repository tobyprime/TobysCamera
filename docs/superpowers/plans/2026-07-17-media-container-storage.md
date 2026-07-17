# Media Container Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-tile files with one indexed compressed container per photo or video.

**Architecture:** `TileContainer` writes independently GZIP-compressed map tiles to a staged `.tbc` file and returns byte ranges. The SQLite repositories persist ranges in new index tables, cache them in memory, and use positional container reads. Existing directories are converted during repository startup.

**Tech Stack:** Java 21 NIO, GZIP, SQLite JDBC, JUnit 5.

---

### Task 1: Container codec

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/TileContainer.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/storage/TileContainerTest.java`

- [ ] Test that two 16,384-byte tiles can be written and individually read by their returned byte ranges.
- [ ] Implement individual GZIP blocks and positional reads with an exact decoded-length check.

### Task 2: Photo repository

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/ShardedMediaLayout.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java`

- [ ] Add the photo tile-range SQLite table and in-memory index.
- [ ] Store new photos at `photos/<prefix>/<uuid>.tbc`; migrate legacy tile directories at startup.
- [ ] Verify persistence, reads, and startup migration.

### Task 3: Video repository

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqliteVideoRepository.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/storage/SqliteVideoRepositoryTest.java`

- [ ] Add video frame/tile byte-range indexing and a reusable per-video read channel.
- [ ] Store new videos at `videos/<prefix>/<uuid>.tbc`; migrate legacy GZIP tile directories at startup.
- [ ] Run `./gradlew.bat :folia:build`.
