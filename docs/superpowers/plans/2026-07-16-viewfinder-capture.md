# Viewfinder Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the direct P-key screenshot flow with a square viewfinder, server-sized capture, preview/confirmation page and deferred tile upload.

**Architecture:** `ViewfinderSession` owns UI state and controls; HUD rendering is isolated in `ViewfinderOverlay`; `CaptureService` delays one frame and produces a square `CapturedFrame`; `PreviewScreen` owns the dynamic texture and submits only confirmed frames. Server grants specify an exact square grid size and the common upload session verifies it.

**Tech Stack:** Java 21, Fabric API 1.21.11, Minecraft GUI/HUD APIs, JUnit 5.

---

## File structure

- `common/.../Packets.java`, `common/.../UploadGrant.java`, `common/.../UploadSession.java`: exact server-selected grid size.
- `fabric/.../viewfinder/`: state machine, overlay, capture service and preview screen.
- `fabric/.../camera/`: square crop/resize processing and upload integration.
- `fabric/.../TobysCameraClient.java`: key dispatch, HUD registration and payload routing.

### Task 1: Enforce the exact grant grid size

**Files:**
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/Packets.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/upload/UploadGrant.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/upload/UploadSession.java`
- Modify: `common/src/test/java/dev/tobyscamera/common/protocol/PacketCodecTest.java`
- Modify: `common/src/test/java/dev/tobyscamera/common/upload/UploadSessionTest.java`

- [ ] **Step 1: Write failing tests for an exact grid**

```java
assertThrows(UploadFailure.class, () -> UploadSession.forGrant(grantFor(2), 1, 1));
assertEquals(2, decodedGrant.gridSize());
```

- [ ] **Step 2: Run the common tests and observe the missing exact-grid API**

Run: `./gradlew.bat :common:test --no-daemon`

Expected: FAIL because the grant does not expose `gridSize` and sessions accept smaller grids.

- [ ] **Step 3: Add `gridSize` to `UploadGranted` and `UploadGrant`**

Store one validated integer in `1..4`; encode/decode it in the existing field position. Make `UploadSession` accept only a width and height equal to `grant.gridSize()`.

- [ ] **Step 4: Run the common tests**

Run: `./gradlew.bat :common:test --no-daemon`

Expected: PASS.

### Task 2: Add image processing independent of Minecraft UI

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/CapturedFrame.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/CenterSquareCropProcessor.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/camera/ResizeToGridProcessor.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/camera/SquareImageProcessorTest.java`

- [ ] **Step 1: Write failing crop and resize tests**

```java
assertEquals(200, crop.process(new BufferedImage(300, 200, TYPE_INT_ARGB)).image().getWidth());
assertEquals(256, resize.process(frame, 2).image().getWidth());
```

- [ ] **Step 2: Run the Fabric test and observe missing processors**

Run: `./gradlew.bat :fabric:test --tests '*SquareImageProcessorTest' --no-daemon`

Expected: FAIL because capture-frame and processor classes do not exist.

- [ ] **Step 3: Implement immutable crop and resize processors**

`CenterSquareCropProcessor` crops the exact center; `ResizeToGridProcessor` uses bilinear interpolation to produce `gridSize * 128` by `gridSize * 128`. `CapturedFrame` owns a `BufferedImage` and grid size.

- [ ] **Step 4: Run Fabric image tests**

Run: `./gradlew.bat :fabric:test --tests '*SquareImageProcessorTest' --no-daemon`

Expected: PASS.

### Task 3: Implement the viewfinder session and HUD

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderState.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/CompositionGrid.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSession.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlay.java`
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSessionTest.java`

- [ ] **Step 1: Write failing state transition tests**

```java
assertEquals(ViewfinderState.AWAITING_GRANT, session.pressShutter());
assertEquals(ViewfinderState.PREVIEW, session.acceptGrant(2));
assertEquals(CompositionGrid.THIRDS, session.cycleGrid());
```

- [ ] **Step 2: Run the state test and observe the missing session classes**

Run: `./gradlew.bat :fabric:test --tests '*ViewfinderSessionTest' --no-daemon`

Expected: FAIL because viewfinder classes do not exist.

- [ ] **Step 3: Implement state, smooth zoom and overlay**

Implement the five design states, `P`/`Esc` close behavior, bounded zoom, `G` grid cycle and one-shot shutter flash. Overlay draws a centered square opening, four outside masks, corners, grid and text HUD; its `captureHidden` flag prevents all overlay drawing during capture.

- [ ] **Step 4: Run the viewfinder test**

Run: `./gradlew.bat :fabric:test --tests '*ViewfinderSessionTest' --no-daemon`

Expected: PASS.

### Task 4: Capture after render and show preview confirmation

**Files:**
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/CaptureService.java`
- Create: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/PreviewScreen.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/camera/PhotoUploadController.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`

- [ ] **Step 1: Write failing capture service tests with a frame supplier**

```java
service.requestAfterNextFrame(2);
assertFalse(service.tick());
assertTrue(service.tick());
```

- [ ] **Step 2: Run the Fabric test and observe the missing capture service**

Run: `./gradlew.bat :fabric:test --tests '*CaptureServiceTest' --no-daemon`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement deferred capture and preview ownership**

`CaptureService` waits one client render frame, hides the overlay, calls `Screenshot.takeScreenshot`, processes the result and restores the overlay. `PreviewScreen` uploads only on its “Use photo” button; “Retake” and close free its dynamic texture and return to viewfinder. `PhotoUploadController` receives the exact grant size and rejects confirm if expired.

- [ ] **Step 4: Run all Fabric tests and compile**

Run: `./gradlew.bat :fabric:test :fabric:classes --no-daemon`

Expected: PASS.

### Task 5: Wire controls, messages and server exact size

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`
- Modify: `README.md`
- Test: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`

- [ ] **Step 1: Write failing server-size and client-message assertions**

```java
assertEquals(configuredGridSize, grant.gridSize());
assertFalse(controller.confirm(expiredFrame));
```

- [ ] **Step 2: Run targeted tests and observe the prior max-grid behavior**

Run: `./gradlew.bat :common:test :folia:test :fabric:test --no-daemon`

Expected: FAIL until coordinator creates exact-size grants and the client routes rejection messages to the session.

- [ ] **Step 3: Wire runtime controls**

Register HUD render callback and client tick controls. Route `UploadGranted`, `RateLimited`, `UploadRejected` and `PhotoCreated` to the session. The server signs `settings.maxGridSize()` as the current exact square grid; future camera capability code changes this single selection point. Update README with P/left-click/wheel/G/Esc controls.

- [ ] **Step 4: Run complete verification and inspect artifacts**

Run: `./gradlew.bat clean test build --no-daemon`

Expected: PASS; Fabric remap jar and Folia jar are created.

## Self-review

Tasks 1 and 5 cover server-selected resolution and server validation. Task 2 covers square image preparation and future processor extension. Tasks 3 and 4 cover Exposure-inspired viewfinder interaction, HUD, delayed capture and preview confirmation. No task adds filters, film, printing, camera stands or offscreen world rendering.
