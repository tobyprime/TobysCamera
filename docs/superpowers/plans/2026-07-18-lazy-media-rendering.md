# Lazy Media Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render only photo, video, and photo-bag maps that are active in a main hand, off hand, or loaded item frame, without loading historical media into memory at startup.

**Architecture:** Add a parser for the persistent media identity embedded in map items and a bounded-lifetime attachment manager driven by hand and item-frame lifecycle events. The manager loads still-map pixels asynchronously and clears each renderer on final source removal. Video playback obtains record metadata and current-frame tiles only for active tagged video maps.

**Tech Stack:** Java 21, Paper/Folia scheduler abstraction, Bukkit map renderers, JUnit 5 and Mockito, SQLite repositories.

---

### Task 1: Parse persistent media identity from map items

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapDescriptor.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MediaMapDescriptorTest.java`

- [ ] **Step 1: Write failing parser tests**

Test that a photo map tagged with `tobyscamera:photo_id`, `tile_x`, and `tile_y` resolves to a photo-tile descriptor; a video map resolves to a video-tile descriptor; a `PhotoBagFactory` bag resolves to a preview descriptor; untagged maps, malformed UUIDs, missing map metadata, and negative coordinates resolve to empty.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapDescriptorTest`

Expected: FAIL because `MediaMapDescriptor` does not exist.

- [ ] **Step 3: Implement the descriptor parser**

Create an immutable descriptor hierarchy containing `mapId`, media UUID, type, and `TileCoordinate` where applicable. Read only root custom data and `MapMeta`; do not query repositories or maintain a map-ID registry.

- [ ] **Step 4: Re-run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapDescriptorTest`

Expected: PASS.

### Task 2: Make map renderers releasable

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/TileMapRenderer.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MutableTileMapRenderer.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/TileMapRendererTest.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MutableTileMapRendererTest.java`

- [ ] **Step 1: Write failing renderer lifecycle tests**

Verify a renderer accepts a valid 16,384-byte tile, renders it, then after `clearPixels()` no longer retains a byte array and renders the neutral blank map; invalid tile sizes still fail.

- [ ] **Step 2: Run renderer tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.TileMapRendererTest --tests dev.tobyscamera.folia.map.MutableTileMapRendererTest`

Expected: FAIL because clearing is unavailable.

- [ ] **Step 3: Implement non-retaining clear support**

Replace immutable pixel fields with volatile nullable fields, keep defensive copies on assignment, provide `setPixels` and `clearPixels`, and make `render` safely draw only when data is present.

- [ ] **Step 4: Re-run renderer tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.TileMapRendererTest --tests dev.tobyscamera.folia.map.MutableTileMapRendererTest`

Expected: PASS.

### Task 3: Implement still-media attachment lifecycle

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/StillMapAttachmentService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagFactory.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/StillMapAttachmentServiceTest.java`

- [ ] **Step 1: Write failing attachment tests**

Using injected map lookup, async executor, and tile loader, verify: attaching two sources to one map starts one load; completion installs pixels; removing one source retains it; removing the final source removes the renderer and clears pixels; a completion after final removal is ignored.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.StillMapAttachmentServiceTest`

Expected: FAIL because the attachment service does not exist.

- [ ] **Step 3: Implement attachment ownership**

Store only source-to-map and active-map attachment state. Attach a blank releasable renderer immediately, load pixels asynchronously via `MapPhotoService.readTile` or `previewPixels`, and apply on the server scheduler only when the attachment generation remains active. Remove only renderers owned by this service and clear every strong pixel reference on final detach.

- [ ] **Step 4: Route new photo and bag creation through inactive maps**

Stop installing permanent pixels in `MapPhotoService.createMaps` and `PhotoBagFactory.create`. Their map views remain locked but blank until a tagged active item activates an attachment.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.StillMapAttachmentServiceTest --tests dev.tobyscamera.folia.map.TileMapRendererTest`

Expected: PASS.

### Task 4: Drive photo and bag attachments from active sources

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagPlacementListener.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MediaMapActivationListenerTest.java`

- [ ] **Step 1: Write failing activation tests**

Verify player reconciliation examines only `getItemInMainHand` and `getItemInOffHand`, never `getContents`; tagged main/off-hand maps attach; changing either hand detaches the prior source; a loaded item frame attaches; removal detaches; and the startup path never calls `PhotoRepository.loadAll` or `VideoRepository.loadAll`.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapActivationListenerTest`

Expected: FAIL because the listener does not exist.

- [ ] **Step 3: Implement lifecycle listeners and frame audit**

Handle player join/quit, held-slot and hand-swap changes, loaded frame add/remove/change, and chunk lifecycle. Reconcile only hand slots. At enable, schedule only loaded-chunk frame scanning. Run a low-frequency scan of loaded frames to reconcile external frame mutations. Wire `PhotoBagPlacementListener` frame mutations and bag hand use to the activation listener.

- [ ] **Step 4: Remove eager restore**

Delete calls to `photos.restore`, `videos.restore`, and eager preview restoration. Register the activation listener after all services are configured and invoke its startup loaded-frame scan. On reload/disable clear every attachment before unregistering listeners or closing storage.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapActivationListenerTest --tests dev.tobyscamera.folia.map.StillMapAttachmentServiceTest`

Expected: PASS.

### Task 5: Refactor video playback to active tagged maps

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/VideoRepository.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqliteVideoRepository.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapVideoService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackService.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/storage/SqliteVideoRepositoryTest.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/video/VideoPlaybackServiceTest.java`

- [ ] **Step 1: Write failing on-demand video tests**

Verify `VideoRepository.find` returns one persisted record without `loadAll`; activating a tagged video tile asynchronously loads only its record and first tile; deactivation clears renderer, tile, record, frame requests, and current-frame pixels; and a restart can activate a video item solely from item tags.

- [ ] **Step 2: Run focused video tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.SqliteVideoRepositoryTest --tests dev.tobyscamera.folia.video.VideoPlaybackServiceTest`

Expected: FAIL because on-demand lookup and active attachment release are unavailable.

- [ ] **Step 3: Implement on-demand video state**

Add `find(UUID)` to the repository. Replace `recordsById`, global map-ID indexes, and the 2,048-entry tile cache with active attachment state derived from descriptors. Load record metadata and frame tiles asynchronously, apply only if the active generation remains current, and release state on final detach. Preserve the existing distance-ordered update budget for active frames.

- [ ] **Step 4: Re-run focused video tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.SqliteVideoRepositoryTest --tests dev.tobyscamera.folia.video.VideoPlaybackServiceTest`

Expected: PASS.

### Task 6: Verify the complete plugin behavior

**Files:**
- Modify: `README.md`
- Modify: `MODRINTH_en_US.md`
- Modify: `MODRINTH_zh_CN.md`

- [ ] **Step 1: Document lazy-rendering behavior**

State that historical media is not loaded on startup, only main/off-hand maps and maps in loaded item frames are attached, and cold storage reads display after the read completes without blocking a server tick.

- [ ] **Step 2: Run complete test suite**

Run: `./gradlew.bat :folia:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect static requirements**

Run: `rg -n "loadAll\\(|photos\\.restore|videos\\.restore|tileCache|recordsById" folia/src/main/java`

Expected: no startup or active-service historical-media index usage remains; repository `loadAll` declarations may remain only for backwards-compatible storage APIs.

- [ ] **Step 4: Commit implementation**

Run:

```powershell
git add folia/src README.md MODRINTH_en_US.md MODRINTH_zh_CN.md docs/superpowers
git commit -m "fix: lazily load active camera media"
```

