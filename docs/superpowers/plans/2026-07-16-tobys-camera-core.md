# TobysCamera Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Fabric client and Folia plugin core that turns a confirmed client-rendered photo into persistent, original-client-readable `filled_map` tiles.

**Architecture:** A Java 21 Gradle multi-project has `common` for byte-level protocol and upload/session rules, `fabric` for client capture UI and tile production, and `folia` for authoritative validation, persistence and map delivery. The client sends pre-quantized 128x128 map-palette tiles; the server only validates, stores and renders them.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Fabric Loom/Fabric API, Folia API, SQLite JDBC, JUnit 5.

---

## File structure

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`: root build and versions.
- `common/`: protocol records, binary codec, token and rate-limit domain logic, JUnit tests.
- `fabric/`: Fabric entrypoint, custom-payload receiver/sender, held-camera test, photo tiler and preview hook.
- `folia/`: plugin entrypoint, packet listener, upload coordinator, SQLite repository, map renderer and delivery service.
- `folia/src/main/resources/plugin.yml`, `config.yml`: Folia plugin metadata and defaults.

### Task 1: Bootstrap the Java multi-project build

**Files:**

- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `common/build.gradle.kts`
- Create: `fabric/build.gradle.kts`
- Create: `folia/build.gradle.kts`

- [ ] **Step 1: Create the failing build layout check**

Add a root Gradle task that fails unless `:common:test`, `:fabric:classes`, and `:folia:classes` exist.

```kotlin
tasks.register("verifyModules") {
    dependsOn(":common:test", ":fabric:classes", ":folia:classes")
}
```

- [ ] **Step 2: Run the layout check to verify it fails**

Run: `./gradlew verifyModules`

Expected: FAIL because the included projects and tasks do not exist.

- [ ] **Step 3: Add the settings and module build files**

Configure Java toolchain 21 for all projects. Include `common`, `fabric`, and `folia`; make Fabric and Folia depend on `project(":common")`. Add JUnit Jupiter to `common` and configure `useJUnitPlatform()`. Declare the Minecraft, Fabric Loader, Fabric API, and Folia API versions in `gradle.properties`, rather than embedding versions in source.

- [ ] **Step 4: Run the layout check**

Run: `./gradlew verifyModules`

Expected: PASS after the configured Fabric and Folia dependencies resolve.

### Task 2: Define and test the binary custom-payload protocol

**Files:**

- Create: `common/src/main/java/dev/tobyscamera/common/protocol/PacketType.java`
- Create: `common/src/main/java/dev/tobyscamera/common/protocol/CameraPacket.java`
- Create: `common/src/main/java/dev/tobyscamera/common/protocol/Packets.java`
- Create: `common/src/main/java/dev/tobyscamera/common/protocol/PacketCodec.java`
- Create: `common/src/test/java/dev/tobyscamera/common/protocol/PacketCodecTest.java`

- [ ] **Step 1: Write failing round-trip tests**

Test `CaptureIntent`, `UploadGranted`, `RateLimited`, `UploadBegin`, one `UploadTileChunk`, `UploadFinish`, `PhotoCreated`, and `UploadRejected`. Assert that decoding the encoded byte buffer yields exactly the original record. Include a chunk payload of 8,192 bytes and reject unknown packet type/version and a chunk whose declared length exceeds 8,192 bytes.

```java
assertEquals(packet, PacketCodec.decode(PacketCodec.encode(packet)));
assertThrows(ProtocolException.class, () -> PacketCodec.decode(unknownTypeBytes));
```

- [ ] **Step 2: Run the protocol tests to verify failure**

Run: `./gradlew :common:test --tests '*PacketCodecTest'`

Expected: FAIL because protocol types and codec do not exist.

- [ ] **Step 3: Implement versioned packet records and codec**

Use protocol version `1`, packet enum byte discriminators, UUID for Token and photo IDs, UTF-8 strings with an explicit maximum length, and big-endian fixed-width fields. Define these records: `CaptureIntent`, `UploadGranted(UUID token, long expiresAtEpochMillis, int maxGridSize, int tileBytes)`, `RateLimited(long retryAfterMillis)`, `UploadBegin(UUID token, int gridWidth, int gridHeight)`, `UploadTileChunk(UUID token, int tileX, int tileY, int offset, byte[] data)`, `UploadFinish(UUID token)`, `PhotoCreated(UUID photoId, List<Integer> mapIds, int gridWidth, int gridHeight)`, and `UploadRejected(String reason)`.

- [ ] **Step 4: Run the protocol tests**

Run: `./gradlew :common:test --tests '*PacketCodecTest'`

Expected: PASS.

### Task 3: Implement token, rate-limit, and tile-assembly domain rules

**Files:**

- Create: `common/src/main/java/dev/tobyscamera/common/upload/RateLimit.java`
- Create: `common/src/main/java/dev/tobyscamera/common/upload/SlidingWindowRateLimiter.java`
- Create: `common/src/main/java/dev/tobyscamera/common/upload/UploadGrant.java`
- Create: `common/src/main/java/dev/tobyscamera/common/upload/UploadSession.java`
- Create: `common/src/main/java/dev/tobyscamera/common/upload/UploadFailure.java`
- Create: `common/src/test/java/dev/tobyscamera/common/upload/SlidingWindowRateLimiterTest.java`
- Create: `common/src/test/java/dev/tobyscamera/common/upload/UploadSessionTest.java`

- [ ] **Step 1: Write failing domain tests**

Cover one-per-second and configurable-per-minute rejection, Token expiry, wrong-player Token, 1x1/2x2/4x4 accepted grids, duplicate/overlapping/out-of-range chunks rejected, and success only after each tile reaches exactly 16,384 bytes.

```java
assertFalse(limiter.tryAcquire(player, Instant.parse("2026-07-16T00:00:00Z")));
assertThrows(UploadFailure.class, () -> session.append(player, 4, 0, 0, bytes));
assertTrue(session.isComplete());
```

- [ ] **Step 2: Run the domain tests to verify failure**

Run: `./gradlew :common:test --tests '*RateLimiterTest' --tests '*UploadSessionTest'`

Expected: FAIL because the domain classes do not exist.

- [ ] **Step 3: Implement deterministic clock-based domain services**

Inject `Clock`; retain only accepted capture timestamps per UUID; grant Tokens as `UUID.randomUUID()` with player UUID, issue time and expiry. Make `UploadSession` validate grid bounds before allocating its at-most-16 16,384-byte tile buffers, accept only contiguous offsets per tile, and expose completed immutable tile bytes.

- [ ] **Step 4: Run all common tests**

Run: `./gradlew :common:test`

Expected: PASS.

### Task 4: Create plugin configuration, lifecycle and packet gateway

**Files:**

- Create: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/config/PluginSettings.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/net/PluginPayloadGateway.java`
- Create: `folia/src/main/resources/plugin.yml`
- Create: `folia/src/main/resources/config.yml`
- Test: `folia/src/test/java/dev/tobyscamera/folia/config/PluginSettingsTest.java`

- [ ] **Step 1: Write failing configuration tests**

Assert default settings load `tobyscamera:camera`, 60-second Token TTL, rates of 1/second and 12/minute, 4 maximum tiles per side, 8,192-byte chunks, and 30-second upload timeout. Assert invalid `max-grid-size` greater than 4 fails validation.

- [ ] **Step 2: Run the configuration test to verify failure**

Run: `./gradlew :folia:test --tests '*PluginSettingsTest'`

Expected: FAIL because settings classes do not exist.

- [ ] **Step 3: Implement plugin lifecycle and custom-payload registration**

On enable, save the default config, construct validated settings, create the persistence and upload services, register `tobyscamera:main`, and decode packets with `PacketCodec`. At the packet entry point, decode only bounded payloads; route `CaptureIntent`, `UploadBegin`, `UploadTileChunk`, and `UploadFinish` to the coordinator. On disable, close SQLite and clear in-memory sessions.

- [ ] **Step 4: Run plugin tests and compile**

Run: `./gradlew :folia:test :folia:classes`

Expected: PASS.

### Task 5: Implement authoritative capture and upload coordination

**Files:**

- Create: `folia/src/main/java/dev/tobyscamera/folia/camera/CameraItemValidator.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/upload/PlayerUploadRegistry.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/sound/ShutterSoundService.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`

- [ ] **Step 1: Write failing coordinator tests using fakes**

Verify an untagged held item does not receive a grant, a rate-limited player receives `RateLimited` and no sound, a valid intent plays sound then sends a grant, invalid/replayed/foreign/expired Token disconnects the player before tile storage, and malformed data under a valid Token returns `UploadRejected` without disconnecting.

- [ ] **Step 2: Run the coordinator test to verify failure**

Run: `./gradlew :folia:test --tests '*UploadCoordinatorTest'`

Expected: FAIL because coordinator interfaces and implementation do not exist.

- [ ] **Step 3: Implement coordinator with explicit side-effect ports**

Check either hand for the configured `custom_data` key at capture and again at upload begin. Apply the rate limiter before sound or Token creation. Bind the grant to player UUID and current camera identity. On an invalid Token in any upload packet, call the disconnect port immediately and return without creating a session, appending bytes, or scheduling storage. For valid sessions, send rejections for tile syntax, timeout and interrupted upload; only complete sessions enter persistence.

- [ ] **Step 4: Run coordinator and common tests**

Run: `./gradlew :folia:test :common:test`

Expected: PASS.

### Task 6: Persist map tiles and recover them after restart

**Files:**

- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRecord.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRepository.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/TileFileStore.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java`

- [ ] **Step 1: Write failing storage tests with a temporary directory/database**

Persist a 2x2 photo with four 16,384-byte tiles and map IDs, reopen the repository, and assert all metadata and bytes return unchanged. Assert an incomplete temporary upload is removed during recovery and a tile of any other size is rejected.

- [ ] **Step 2: Run the storage test to verify failure**

Run: `./gradlew :folia:test --tests '*SqlitePhotoRepositoryTest'`

Expected: FAIL because persistence classes do not exist.

- [ ] **Step 3: Implement atomic tile and SQLite persistence**

Store tiles under `plugins/TobysCamera/photos/<photo-uuid>/<x>-<y>.tile`, write to a per-photo temporary directory, atomically move it to its final location, then commit a SQLite transaction containing photo and tile-to-map rows. On startup remove temporary directories and load all completed records. Reject inconsistent rows or missing tile files from renderer registration and log their photo IDs.

- [ ] **Step 4: Run storage tests**

Run: `./gradlew :folia:test --tests '*SqlitePhotoRepositoryTest'`

Expected: PASS.

### Task 7: Create and restore static filled-map renderers; deliver maps

**Files:**

- Create: `folia/src/main/java/dev/tobyscamera/folia/map/TileMapRenderer.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/delivery/MapDeliveryService.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/delivery/PendingDeliveryRepository.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/delivery/MapDeliveryServiceTest.java`

- [ ] **Step 1: Write failing delivery and restoration tests**

With fake map and player ports, assert one map item per tile contains photo UUID and tile/grid metadata, inventory is attempted before a world drop, an offline player gets a queued delivery, and startup restoration registers every persisted map ID with the correct immutable tile bytes.

- [ ] **Step 2: Run the map test to verify failure**

Run: `./gradlew :folia:test --tests '*MapDeliveryServiceTest'`

Expected: FAIL because map and delivery services do not exist.

- [ ] **Step 3: Implement map/delivery services with Folia scheduling**

For each completed tile, create a `MapView`, remove its default renderers, attach a non-contextual `TileMapRenderer` backed by immutable 16,384-byte data, and produce a `FILLED_MAP` item whose `custom_data` includes photo UUID, `tile_x`, `tile_y`, `grid_width`, and `grid_height`. Keep Bukkit map creation, inventory addition, item drop and player response on the player/entity scheduler. On enable restore renderers from `PhotoRepository`; on join deliver queued items.

- [ ] **Step 4: Run all plugin tests**

Run: `./gradlew :folia:test`

Expected: PASS.

### Task 8: Add Fabric payload integration and client tile producer

**Files:**

- Create: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/net/ClientPayloadGateway.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/HeldCameraChecker.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/MapTileEncoder.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/PhotoUploadController.java`
- Test: `fabric/src/test/java/dev/tobyscamera/fabric/camera/MapTileEncoderTest.java`

- [ ] **Step 1: Write failing tile-encoder tests**

Use synthetic ARGB inputs. Assert a 128x128 source produces one 16,384-byte tile, a 256x256 source produces four tiles in row-major coordinate order, a 512x512 source produces sixteen, and larger input is downscaled to a maximum 4x4 grid before palette quantization.

- [ ] **Step 2: Run tile tests to verify failure**

Run: `./gradlew :fabric:test --tests '*MapTileEncoderTest'`

Expected: FAIL because client tile classes do not exist.

- [ ] **Step 3: Implement client protocol flow**

Register the payload receiver and sender for `tobyscamera:main`. Let the camera UI open only when `HeldCameraChecker` finds the configured `custom_data` key in either hand. Send `CaptureIntent`; on `UploadGranted`, keep the Token while the preview is open. On confirmation, use `MapTileEncoder` then send `UploadBegin`, 8,192-byte `UploadTileChunk`s in ascending tile Y/X and offset order, and `UploadFinish`. On `RateLimited` and `UploadRejected`, show the client notice and do not upload. Stop an upload before local Token expiry.

- [ ] **Step 4: Run Fabric tests and compile**

Run: `./gradlew :fabric:test :fabric:classes`

Expected: PASS.

### Task 9: Wire end-to-end behavior and verify artifacts

**Files:**

- Modify: `README.md`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`

- [ ] **Step 1: Write the manual integration checklist in README**

Document installing the Fabric jar only for the photographer and the Folia jar on the server; creating a camera-tagged item; taking a 1x1 and 4x4 image; verifying original client viewing, inventory overflow, server restart restoration, rate-limit response, and invalid-Token disconnect.

- [ ] **Step 2: Run all automated checks**

Run: `./gradlew clean test build`

Expected: all unit tests pass and Fabric/Folia jars are produced under their module `build/libs` directories.

- [ ] **Step 3: Run the plugin descriptor inspection**

Run: `jar tf folia/build/libs/*.jar | Select-String 'plugin.yml|config.yml'`

Expected: both resources are included in the plugin jar.

## Self-review

Spec coverage: Tasks 2–3 cover protocol, Tokens, limits and fixed tile validation; Tasks 4–5 cover Folia authority, sound and invalid-Token disconnect; Task 6 covers atomic persistence and restart recovery; Task 7 covers map rendering and delivery; Task 8 covers client-side capture, quantization and upload; Task 9 covers build and manual validation.

The plan intentionally does not add camera parameter controls, resolution settings or film consumption. All future capture context remains optional and versioned in the protocol.
