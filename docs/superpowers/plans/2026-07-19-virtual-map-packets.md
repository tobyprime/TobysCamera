# Virtual Map Packets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render persistent camera photos through plugin-owned virtual map IDs and standard vanilla map packets without creating native map data.

**Architecture:** Persist a monotonic virtual ID allocator above the vanilla map-ID high-water mark and advance the vanilla allocator beyond every issued virtual ID. Replace Bukkit map creation/renderers with virtual map items and a per-player packet dispatcher driven by the existing hand and sent-chunk lifecycle.

**Tech Stack:** Java 21, Paper 1.21.11 NMS, Bukkit/Paper API, SQLite, JUnit 5, Mockito.

---

### Task 1: Define collision-free virtual ID allocation

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/VirtualMapIdRepository.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/VanillaMapIdFloor.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/storage/VirtualMapIdRepositoryTest.java`

- [ ] Add failing tests proving first allocation is strictly above a supplied vanilla floor, allocations never repeat after reopening SQLite, and allocating `n` advances the injected vanilla floor to at least `n + 1`.
- [ ] Implement a transactional `virtual_map_ids` singleton row with `next_id`, `floor_id`, and strict positive integer validation.
- [ ] Implement the Paper adapter that reads and advances the vanilla map-ID counter; do not create `MapItemSavedData` while reserving IDs.
- [ ] Run `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.VirtualMapIdRepositoryTest`.

### Task 2: Store virtual IDs instead of creating Bukkit maps

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagFactory.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MapPhotoServiceTest.java`

- [ ] Add failing tests proving a 2x2 record receives four allocator IDs, photo-bag creation receives one allocator ID, and no injected Bukkit map factory is invoked.
- [ ] Inject `VirtualMapIdRepository` into photo and bag creation, retain integer IDs in `PhotoRecord`, and use `MapMeta.setMapId` for virtual photo and preview items.
- [ ] Remove `Bukkit.createMap` and `Bukkit.getMap` dependencies from photo creation and item manufacture.
- [ ] Run the focused map-photo and bag tests.

### Task 3: Send standard map packets without MapView

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualMapPacketSender.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualStillMapService.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/map/VirtualStillMapServiceTest.java`

- [ ] Add failing tests proving one active player receives a full 128x128 packet with the descriptor's virtual ID, two sources sharing a tile load once, and unchanged pixels are not resent to the same player.
- [ ] Implement an NMS packet adapter for `ClientboundMapItemDataPacket` and an injected sender seam for unit tests.
- [ ] Implement asynchronous tile loading, reference-counted source attachment, per-player revision deduplication, and clearing all pixels after final detach.
- [ ] Run the focused virtual-map tests.

### Task 4: Route source activation to packet recipients

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/ChunkFrameViewerTracker.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MediaMapActivationListenerTest.java`

- [ ] Add failing tests proving main/off-hand sources send only to their owner, frame sources send only to the viewer whose chunk was delivered, and chunk unload stops future sends.
- [ ] Replace `StillMapAttachmentService` calls with `VirtualStillMapService`, carrying the viewer UUID in every source registration.
- [ ] Preserve asynchronous I/O and Folia entity/region ownership rules.
- [ ] Run activation and virtual-map focused tests.

### Task 5: Protect custom maps from native map operations and verify

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/CameraMapCopyMetadataListener.java`
- Modify: `README.md`
- Modify: `MODRINTH_en_US.md`
- Modify: `MODRINTH_zh_CN.md`

- [ ] Add tests showing a custom camera map retains its virtual ID through plugin-supported copy paths and no path resolves it through `Bukkit.getMap`.
- [ ] Reject or take ownership of vanilla cartography mutations that would require native map data.
- [ ] Run `./gradlew.bat :folia:test` and `./gradlew.bat :common:test`.
- [ ] Search production sources for `Bukkit.createMap`, `Bukkit.getMap`, and `MapRenderer` in the camera-photo path; expected result is no remaining virtual-photo dependency.
