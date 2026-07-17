# Video Performance Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove palette encoding from the Fabric client tick and remove video tile file I/O from Folia's global playback tick.

**Architecture:** A small asynchronous encoder owns one requested `VideoEncoder` frame and exposes a non-blocking polling API to `VideoUploadController`. On the server, `MapVideoService` separates cache preloading from cached-frame application; `VideoPlaybackService` schedules cache misses on Folia's async scheduler and only applies cached pixels from its global tick.

**Tech Stack:** Java 21, JUnit 5, Fabric client tick, Paper/Folia schedulers.

---

### Task 1: Add a non-blocking client frame encoder

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/AsyncVideoFrameEncoder.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/AsyncVideoFrameEncoderTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void doesNotEncodeUntilItsBackgroundTaskRuns() throws Exception {
    var queued = new ArrayDeque<Runnable>();
    var encoded = new AtomicInteger();
    var output = new MapTileEncoder.EncodedPhoto(1, 1, List.of(new byte[16_384]));
    AsyncVideoFrameEncoder frames = new AsyncVideoFrameEncoder(queued::add);

    frames.request(0, ignored -> { encoded.incrementAndGet(); return output; });

    assertNull(frames.poll(0));
    assertEquals(0, encoded.get());
    queued.remove().run();
    assertSame(output, frames.poll(0));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.video.AsyncVideoFrameEncoderTest --no-daemon`

Expected: compilation failure because `AsyncVideoFrameEncoder` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
final class AsyncVideoFrameEncoder {
    private final Executor executor;
    private int requested = -1;
    private MapTileEncoder.EncodedPhoto result;
    private IOException failure;

    void request(int frame, FrameEncoder encoder) {
        if (requested >= 0) return;
        requested = frame;
        executor.execute(() -> { try { result = encoder.encode(frame); } catch (IOException e) { failure = e; } });
    }
    MapTileEncoder.EncodedPhoto poll(int frame) throws IOException { /* return only completed matching result */ }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.video.AsyncVideoFrameEncoderTest --no-daemon`

Expected: PASS.

### Task 2: Make video upload consume only background-encoded frames

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/video/VideoUploadController.java:21-114`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoUploadControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void waitsForTheBackgroundFrameBeforeSendingTileChunks() throws Exception {
    var queued = new ArrayDeque<Runnable>();
    VideoUploadController controller = new VideoUploadController(sent::add, now::get, failure::set, queued::add);
    // begin and grant a one-frame video
    controller.tick();
    assertEquals(0, tileChunks(sent));
    queued.remove().run();
    controller.tick();
    assertEquals(2, tileChunks(sent));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.video.VideoUploadControllerTest --no-daemon`

Expected: compilation failure because the executor-injection constructor and non-blocking behavior do not exist.

- [ ] **Step 3: Write minimal implementation**

Use `AsyncVideoFrameEncoder` in `sendNextChunk`: request `encoder.frame(frame)` when no result is ready, return from the tick without sending a tile, and emit chunks only after `poll(frame)` returns. Reset/cancel the encoder from `clear`. Keep preview chunks, rate limiting, packet order, and progress totals unchanged.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.video.VideoUploadControllerTest --no-daemon`

Expected: PASS.

### Task 3: Split cache preloading from cached video-frame application

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/VideoTileCache.java:20-25`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapVideoService.java:90-95`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/map/VideoTileCacheTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void reportsACacheMissWithoutInvokingTheLoader() throws Exception {
    VideoTileCache cache = new VideoTileCache(1);
    assertNull(cache.find(new VideoTileCache.Key(UUID.randomUUID(), 0, new TileCoordinate(0, 0))));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VideoTileCacheTest --no-daemon`

Expected: compilation failure because `find` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add `find(Key)` to return cached bytes without loading. Replace `showFrame` with `preloadFrame`, which may read the repository and fill the cache, and `showCachedFrame`, which returns `null` on a miss and otherwise updates the renderer from cache. Neither cached application method may call the repository.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VideoTileCacheTest --no-daemon`

Expected: PASS.

### Task 4: Schedule cache misses outside the global playback tick

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackService.java:80-103`
- Test: `folia/src/test/java/dev/tobyscamera/folia/video/VideoPlaybackIndexTest.java`

- [ ] **Step 1: Write the failing test**

Add a package-visible helper test that accepts a cache-miss callback and asserts that repeated requests for the same `(videoId, mapId, frameIndex)` invoke that callback once while pending.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.video.VideoPlaybackIndexTest --no-daemon`

Expected: failure because playback has no in-flight-load deduplication helper.

- [ ] **Step 3: Write minimal implementation**

Maintain a concurrent set of `(videoId, mapId, frameIndex)` requests. When `showCachedFrame` returns `null`, enqueue one `AsyncScheduler.runNow` call to `preloadFrame`; remove the key in `finally`. Only set `lastSentFrame` and call `MapUpdateDispatcher.send` after `showCachedFrame` returns a map.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.video.VideoPlaybackIndexTest --no-daemon`

Expected: PASS.

### Task 5: Verify the two performance paths together

**Files:**
- Verify: `fabric/src/test/java/dev/tobyscamera/fabric/video/AsyncVideoFrameEncoderTest.java`
- Verify: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoUploadControllerTest.java`
- Verify: `folia/src/test/java/dev/tobyscamera/folia/map/VideoTileCacheTest.java`
- Verify: `folia/src/test/java/dev/tobyscamera/folia/video/VideoPlaybackIndexTest.java`

- [ ] **Step 1: Run focused test suites**

Run: `./gradlew.bat :fabric:test :folia:test --no-daemon`

Expected: PASS.

- [ ] **Step 2: Run the full repository suite**

Run: `./gradlew.bat test --no-daemon --rerun-tasks`

Expected: PASS with no test failures.

- [ ] **Step 3: Review final diff**

Run: `git diff --check HEAD~1..HEAD && git status --short`

Expected: no whitespace errors and only the intended source, tests, and documentation changes.
