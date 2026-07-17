# Video Playback Index Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove server preview regeneration and select active videos/viewers from nearby spatial buckets.

**Architecture:** Preview services accept only persisted client bytes. A pure spatial index owns chunk-bucket membership and selection; `VideoPlaybackService` updates it from Bukkit events and only schedules map updates for its computed viewer set.

**Tech Stack:** Java 21, JUnit 5, Paper/Folia schedulers, Bukkit maps.

---

### Task 1: Remove old preview reconstruction

**Files:** `folia/.../map/{MapPhotoService,MapVideoService,MapPreviewEncoder}.java`, preview tests.

- [ ] Write tests that a missing preview is rejected without tile reads.
- [ ] Remove `MapPreviewEncoder`, fallback code, and legacy fallback tests.
- [ ] Run `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.BagPreviewRestoreTest`.

### Task 2: Build a pure spatial playback index

**Files:** Create `folia/.../video/VideoPlaybackIndex.java`; test it beside `ActiveVideoMapSelectorTest`.

- [ ] Write chunk-boundary, world, held-map, de-duplication, distance, and limit tests.
- [ ] Implement immutable snapshots and indexed frame/player membership keyed by world and chunk.
- [ ] Run the focused index tests.

### Task 3: Wire event updates and verify playback

**Files:** `folia/.../video/VideoPlaybackService.java`, its tests.

- [ ] Replace per-tick full scans and nested viewer lookup with index updates and one snapshot query.
- [ ] Run `./gradlew.bat :folia:test` followed by `./gradlew.bat build`.
