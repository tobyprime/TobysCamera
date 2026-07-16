# Dynamic Map Video Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record client video, upload trimmed/finally-sized map tiles, and play persistent dynamic map videos within a distance-ordered update budget.

**Architecture:** Keep photos intact. Add a dedicated video upload protocol/session and video repository. The Fabric client records temporary source frames, then produces a selected encoded video at confirmation. The Folia plugin owns map IDs, persistent frames, and a global playback selector that updates at most the configured number of individual map frames.

**Tech Stack:** Java 21, Fabric 1.21.11, Paper/Folia 1.21.11, Bukkit MapView/MapRenderer, SQLite, JUnit 5.

---

### Task 1: Version the protocol and video upload primitives

**Files:**
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/PacketType.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/Packets.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/PacketCodec.java`
- Create: `common/src/main/java/dev/tobyscamera/common/upload/VideoUploadSession.java`
- Create: `common/src/test/java/dev/tobyscamera/common/protocol/VideoPacketCodecTest.java`
- Create: `common/src/test/java/dev/tobyscamera/common/upload/VideoUploadSessionTest.java`

- [ ] Write failing round-trip tests for `VideoBegin(3,4,10,20)`, `VideoGranted`, one `VideoTileChunk`, `VideoFinish`, and `VideoCreated`.
- [ ] Run `./gradlew.bat :common:test --tests dev.tobyscamera.common.protocol.VideoPacketCodecTest --no-daemon`; expect missing video packet types.
- [ ] Add packet types and codec branches for `VIDEO_BEGIN`, `VIDEO_GRANTED`, `VIDEO_TILE_CHUNK`, `VIDEO_FINISH`, and `VIDEO_CREATED`. Preserve protocol byte version compatibility by appending new packet IDs only.
- [ ] Write failing `VideoUploadSessionTest` cases for accepting every 16,384-byte tile once per `(frame,x,y)`, rejecting duplicate/out-of-range chunks, and reporting complete only after `frames * width * height` tiles are present.
- [ ] Implement `VideoUploadSession` with `append`, `tile`, `complete`, dimensions/FPS/frame count accessors, and the same 8,192-byte chunk constraints as photos.
- [ ] Re-run both focused tests; expect PASS. Commit `feat: add video upload protocol`.

### Task 2: Add server video settings and camera FPS component support

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/config/PluginSettings.java`
- Modify: `folia/src/main/resources/config.yml`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/camera/CameraFilmService.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/config/PluginSettingsTest.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/camera/CameraFilmServiceTest.java`

- [ ] Add failing tests that defaults are FPS 10, maximum frames 100, upload chunks/s 120, active map frames 128, and that FPS over 20 or below 1 is rejected.
- [ ] Run the focused Folia setting tests; expect constructor/accessor failures.
- [ ] Extend `PluginSettings` with `videoMaxFps`, `videoMaxFrames`, `videoUploadChunksPerSecond`, and `videoMaxActiveMapFrames`; validate positive values and `videoMaxFps <= 20`.
- [ ] Add `tobyscamera:max_video_fps` lookup to `CameraFilmService`, falling back to `videoMaxFps`, and cap the result to the configured maximum.
- [ ] Add matching YAML keys and tests for camera-specific FPS caps. Re-run focused tests; expect PASS. Commit `feat: configure video recording limits`.

### Task 3: Implement server-side video grant, charging, and chunk-rate enforcement

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/upload/VideoUploadCoordinator.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/net/PluginPayloadGateway.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/upload/VideoUploadCoordinatorTest.java`

- [ ] Write failing tests proving `VideoBegin(3,4,10,20)` consumes 240 film, grants the configured chunk rate, rejects FPS/frame limits before charging, and rejects the 121st chunk in a one-second 120-chunk window.
- [ ] Run the focused coordinator test; expect missing coordinator behavior.
- [ ] Implement a video token/session map separate from photo sessions. On begin, validate tagged held camera, grid/FPS/frame count, compute `frames * width * height` with `Math.multiplyExact`, and consume film once before issuing `VideoGranted`.
- [ ] Enforce a per-token `SlidingWindowRateLimiter` before appending each video chunk. Clear the session and reply `UploadRejected` on rate overrun; retain the existing kick policy for invalid tokens.
- [ ] Route the new packet variants through `PluginPayloadGateway` and construct the coordinator in `TobysCameraPlugin`.
- [ ] Re-run focused tests; expect PASS. Commit `feat: validate and rate limit video uploads`.

### Task 4: Persist videos and create dynamic video map items

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/VideoRecord.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/VideoRepository.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/SqliteVideoRepository.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/MapVideoService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/storage/SqliteVideoRepositoryTest.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/map/MapVideoServiceTest.java`

- [ ] Write failing repository tests that save/load a two-frame 2x1 video, preserve dimensions/FPS/owner metadata, and return each 16,384-byte tile by `(video, frame, coordinate)`.
- [ ] Run the focused repository test; expect missing video tables/repository.
- [ ] Add `videos`, `video_maps`, and `video_tiles` SQLite tables. Store compressed tile data and create indexes for video/frame lookup.
- [ ] Write failing map-service tests that create one normal map ID per tile and add root custom-data `tobyscamera:video_id`, `tile_x`, `tile_y`, `grid_width`, and `grid_height`.
- [ ] Implement `MapVideoService.createMaps`, `persist`, `restore`, and `mapItem`; reuse photo metadata/lore conventions while identifying videos distinctly.
- [ ] Re-run focused tests; expect PASS. Commit `feat: persist dynamic map videos`.

### Task 5: Add budgeted looping map playback

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackClock.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/video/ActiveVideoMapSelector.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/video/VideoPlaybackClockTest.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/video/ActiveVideoMapSelectorTest.java`

- [ ] Write failing clock tests showing a 3-frame, 10-FPS video maps elapsed milliseconds to `0,1,2,0`.
- [ ] Run the clock test; expect missing clock.
- [ ] Implement `VideoPlaybackClock.frameAt(video, nowMillis)` using modulo frame count and elapsed time, with no tick accumulation drift.
- [ ] Write failing selector tests with 130 map-frame candidates and two player locations; assert the nearest 128 individual maps are selected regardless of grid grouping.
- [ ] Implement selector ordering by each map frame's minimum squared distance to online players, then a stable map ID tie-breaker.
- [ ] Implement playback service to discover video maps in item frames, select active maps every server tick, and refresh selected MapViews with their video's current tile. Schedule it with Folia's global region scheduler in plugin startup and cancel it on disable.
- [ ] Re-run focused video tests; expect PASS. Commit `feat: play dynamic maps within distance budget`.

### Task 6: Deliver completed video prints

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/delivery/MapDeliveryService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/VideoUploadCoordinator.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/upload/VideoUploadCoordinatorTest.java`

- [ ] Write failing completion test that a completed video session persists before delivery, delivers every dynamic map item, and emits `VideoCreated` with video dimensions/FPS/frame count.
- [ ] Run the focused test; expect no completion handler wiring.
- [ ] Wire `VideoUploadCoordinator` completion through `MapVideoService.createMaps`, asynchronous persistence, owner delivery/queueing, and `VideoCreated` response on the player scheduler.
- [ ] Re-run focused test; expect PASS. Commit `feat: deliver completed video map prints`.

### Task 7: Add client video mode, FPS controls, and capture scheduling

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/input/CameraKeyBindings.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSession.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderInputController.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlay.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/VideoCaptureService.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoCaptureServiceTest.java`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSessionTest.java`

- [ ] Write failing session tests for switching photo/video modes, refusing FPS outside `1..maxVideoFps`, and transitioning recording to preview only after stop.
- [ ] Run focused session tests; expect missing mode/FPS methods.
- [ ] Add configurable mode and FPS key mappings, session state for selected mode/FPS, and overlay labels displaying `VIDEO`, FPS, and recording elapsed frame count.
- [ ] Write failing capture-service tests for due-frame times at 10 FPS and for no captures after stop.
- [ ] Implement `VideoCaptureService` with monotonic deadlines; expose a capture-ready signal consumed at the existing pre-hand screenshot injection point so GUI/hands/name tags stay absent.
- [ ] Re-run focused Fabric tests; expect PASS. Commit `feat: add viewfinder video recording mode`.

### Task 8: Store source frames, trim in preview, and create encoded video frames

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/TemporaryVideoRecording.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/VideoFrameRange.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/VideoEncoder.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/VideoPreviewScreen.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoFrameRangeTest.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoEncoderTest.java`

- [ ] Write failing range tests for inclusive trimming, rejecting a start after end, and calculating retained count.
- [ ] Run the focused range test; expect missing range type.
- [ ] Implement `VideoFrameRange` and disk-backed `TemporaryVideoRecording` with deterministic frame filenames and cleanup on cancel/success/startup.
- [ ] Write failing encoder test that two source frames at a selected rectangular `PrintLayout` produce two encoded photos with the exact selected grid dimensions and palette-preview bytes.
- [ ] Implement `VideoEncoder` to read retained temporary frames, apply `PrintCanvasProcessor`, use `MapTileEncoder`, and expose frames lazily for upload.
- [ ] Implement `VideoPreviewScreen` with frame scrubber, start/end controls, print-size cycle, dithering cycle, and use/cancel actions. Make its visual preview decode the current encoded tile data.
- [ ] Re-run focused tests; expect PASS. Commit `feat: add video trim and print confirmation`.

### Task 9: Upload video frames with the server grant rate

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/ChunkTokenBucket.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/video/VideoUploadController.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/ChunkTokenBucketTest.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/video/VideoUploadControllerTest.java`

- [ ] Write failing token-bucket tests showing a 120 chunks/s grant permits 120 immediate chunks and delays the next one until the next allowance interval.
- [ ] Run the focused bucket test; expect missing bucket.
- [ ] Implement monotonic `ChunkTokenBucket` without sleeps; drive sending from client ticks.
- [ ] Write failing upload-controller tests for begin/grant/chunk/finish order, frame/tile chunk coordinates, and clearing temporary recordings after `VideoCreated` or rejection.
- [ ] Implement `VideoUploadController` to send `VideoBegin`, wait for `VideoGranted`, send at most the token-bucket allowance each tick, then send `VideoFinish` after all frame tiles.
- [ ] Re-run focused tests; expect PASS. Commit `feat: upload rate limited map videos`.

### Task 10: Integrate, verify, and document configuration

**Files:**
- Modify: `folia/src/main/resources/config.yml`
- Modify: `README.md` if present, otherwise create `docs/video-recording.md`

- [ ] Add documented defaults and examples for `tobyscamera:max_video_fps`, `video-max-fps`, `video-max-frames`, `video-max-upload-chunks-per-second`, and `video-max-active-map-frames`.
- [ ] Run `git diff --check`; expect no output.
- [ ] Run `$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890'; .\gradlew.bat test :fabric:remapJar :fabric:verifyPublishedJar :folia:jar --no-daemon`; expect `BUILD SUCCESSFUL`.
- [ ] Manually run the Fabric client and Folia test server: record a short video, trim both ends, select a rectangular print size, confirm upload throttling, place the maps in frames, and verify looping playback plus the 128-map distance budget.
- [ ] Commit `feat: complete dynamic map video recording`.
