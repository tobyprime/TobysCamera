# Player-local Media Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict periodic item-frame auditing to loaded chunks within 128 blocks of online players while respecting Folia region ownership.

**Architecture:** The player entity scheduler snapshots each player's world and chunk coordinate. A small range helper enumerates nearby chunk coordinates, then `MediaMapActivationListener` dispatches each loaded candidate chunk to `runRegion` before reading its entities.

**Tech Stack:** Java 21, Paper/Folia scheduler API, JUnit 5.

---

### Task 1: Define the player-local chunk range

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/PlayerLocalChunkRange.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/map/PlayerLocalChunkRangeTest.java`

- [ ] **Step 1: Write a failing range test**

```java
@Test
void includesOnlyChunksWhoseAreaCanIntersectThe128BlockRadius() {
    assertEquals(289, PlayerLocalChunkRange.around(0, 0, 128).size());
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.PlayerLocalChunkRangeTest`

Expected: compilation failure because `PlayerLocalChunkRange` does not exist.

- [ ] **Step 3: Implement the immutable range helper**

Create `PlayerLocalChunkRange.around(int chunkX, int chunkZ, int radiusBlocks)` returning chunk-coordinate values from `floorDiv(radiusBlocks + 15, 16)` in each direction, including the center chunk.

- [ ] **Step 4: Re-run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.PlayerLocalChunkRangeTest`

Expected: PASS.

### Task 2: Replace the periodic global audit

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/PlayerLocalChunkRangeTest.java`

- [ ] **Step 1: Write a failing source-level scheduling test**

Verify the listener exposes `auditNearPlayers()`, iterates online players, uses `runEntity` to snapshot player location, and uses `runRegion` for every candidate chunk instead of reading entities from the player task.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.PlayerLocalChunkRangeTest`

Expected: FAIL because the player-local audit API is absent.

- [ ] **Step 3: Implement player-local region dispatch**

Add `auditNearPlayers()` to schedule one entity task per online player. The entity task snapshots world and chunk coordinates, enumerates `PlayerLocalChunkRange.around(..., 128)`, checks `world.isChunkLoaded(x, z)`, and schedules each candidate through `scheduler.runRegion`. The region task obtains that chunk and reconciles only its item frames.

- [ ] **Step 4: Wire the recurring task**

Keep `scanLoadedFrames()` for startup only. Change `mediaAuditTask` to call `mediaActivation.auditNearPlayers()` every 200 ticks.

- [ ] **Step 5: Re-run the complete Folia tests**

Run: `./gradlew.bat :folia:test`

Expected: BUILD SUCCESSFUL.
