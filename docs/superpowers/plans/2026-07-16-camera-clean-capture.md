# Camera Clean Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture the zoomed world without the hand or GUI and render preview images at any source aspect ratio without distortion.

**Architecture:** `CaptureService` remains a tick-driven capture scheduler but exposes a one-shot request to a `GameRenderer` mixin. The mixin copies the main render target between world and hand rendering. `PreviewScreen` derives a contain-fitted destination rectangle and sends independent destination, source, and texture dimensions to `GuiGraphics.blit`.

**Tech Stack:** Java 21, Fabric Loom, Fabric 1.21.11 official mappings, Mixin, JUnit 5.

---

### Task 1: Test capture-state and preview geometry contracts

**Files:**
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSessionTest.java`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/PreviewScreenTest.java`

- [ ] **Step 1: Write the failing tests**

```java
assertTrue(session.zoomActive()); // after acceptGrant(1)

PreviewScreen.TextureBlit landscape = PreviewScreen.textureBlit(0, 0, 512, 256, 400, 400);
assertEquals(400, landscape.width());
assertEquals(200, landscape.height());
assertEquals(0, landscape.left());
assertEquals(100, landscape.top());
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.viewfinder.ViewfinderSessionTest --tests dev.tobyscamera.fabric.viewfinder.PreviewScreenTest --no-daemon`

Expected: failure because `CAPTURING` does not activate zoom and the preview geometry API only accepts a square size.

- [ ] **Step 3: Implement the minimal contracts**

```java
public boolean zoomActive() {
    return state == ViewfinderState.VIEWFINDER
        || state == ViewfinderState.AWAITING_GRANT
        || state == ViewfinderState.CAPTURING;
}
```

Change `textureBlit` to accept available width and height, compute `scale = min(availableWidth / sourceWidth, availableHeight / sourceHeight)`, and center the rounded destination rectangle.

- [ ] **Step 4: Re-run the focused tests and verify they pass**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

### Task 2: Move the screenshot copy before hand and GUI rendering

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/CaptureService.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/mixin/CameraMixin.java`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/CaptureServiceTest.java`

- [ ] **Step 1: Write the failing scheduler test**

```java
service.requestAfterNextFrame(2);
assertFalse(service.tick());
assertTrue(service.tick());
assertEquals(2, service.takeGridSize());
```

Keep the request pending until the mixin consumes it; no screenshot may be initiated by `END_CLIENT_TICK`.

- [ ] **Step 2: Run the focused test and verify it fails if needed**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.viewfinder.CaptureServiceTest --no-daemon`

- [ ] **Step 3: Implement the render-stage handoff**

The client tick calls only `CAPTURE.tick()`. Add a public static method that consumes the prepared grid size and calls `Screenshot.takeScreenshot`.

Inject `CameraMixin` into `GameRenderer.render` at the `renderItemInHand` invocation with `shift = BEFORE`, then call the public client method. This location is after `renderLevel` and before first-person hand and GUI composition.

- [ ] **Step 4: Re-run the focused test and compile Fabric**

Run: `./gradlew.bat :fabric:test --tests dev.tobyscamera.fabric.viewfinder.CaptureServiceTest --no-daemon`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Verify and commit

**Files:**
- Modify: the files from Tasks 1 and 2

- [ ] **Step 1: Run all tests**

Run: `./gradlew.bat test --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect the diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors and no staged user-owned `subagent-test/` content.

- [ ] **Step 3: Commit the implementation**

```bash
git add fabric/src/main/java fabric/src/test/java
git commit -m "feat: capture clean zoomed camera frames"
```
