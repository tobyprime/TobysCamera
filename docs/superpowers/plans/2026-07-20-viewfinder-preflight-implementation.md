# Viewfinder Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist a default server-valid print size in the viewfinder, show its layout/cost/remaining-shot estimate, and retain a preview-only resolution override.

**Architecture:** `ViewfinderSettings` and `ViewfinderSession` own the selected default print side length. `ViewfinderOverlay` derives a pure preflight readout from the session, held-camera maximum, film count, and aspect ratio. `PreviewScreen` starts from that default but keeps its existing, local-to-the-preview resolution selector; the selected preview resolution is passed to image processing and upload.

**Tech Stack:** Java 21/25, Fabric Loom, JUnit 5, Minecraft GUI components.

---

### Task 1: Define and test preflight selection/readout

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSession.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSettings.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSettingsStore.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlay.java`
- Test: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSessionTest.java`
- Test: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderOverlayTest.java`

- [ ] **Step 1: Write failing tests**

```java
@Test
void selectedPrintSizeIsClampedToTheCameraMaximum() {
    ViewfinderSession session = new ViewfinderSession();
    session.setPrintSize(6, 4);
    assertEquals(4, session.printSize());
}

@Test
void preflightShowsMapLayoutCostCapacityAndRemainingShots() {
    var readout = ViewfinderOverlay.preflightReadout(4, 6, AspectRatio.of(3, 2), 64);
    assertEquals("4x3 maps", readout.layout());
    assertEquals("12 film", readout.cost());
    assertEquals("film 64 · 5 shots · max 6x6", readout.capacity());
}
```

- [ ] **Step 2: Run the focused tests and verify expected failures**

Run: `./gradlew.bat :fabric-1.21.11:test --tests '*ViewfinderSessionTest' --tests '*ViewfinderOverlayTest' --no-daemon --console=plain`

Expected: compilation failure because `setPrintSize`, `printSize`, and `preflightReadout` do not exist.

- [ ] **Step 3: Implement the minimal selection and readout API**

```java
static PreflightReadout preflightReadout(int side, int maximum, AspectRatio ratio, int remainingFilm) {
    PrintLayout layout = PrintLayout.forMaximumSide(side, ratio);
    int maps = layout.gridWidth() * layout.gridHeight();
    return new PreflightReadout("%dx%d maps".formatted(layout.gridWidth(), layout.gridHeight()),
        remainingFilm < 0 ? "no film required" : "%d film".formatted(maps),
        remainingFilm < 0 ? "max %dx%d".formatted(maximum, maximum)
            : "film %d · %d shots · max %dx%d".formatted(Math.max(0, remainingFilm), remainingFilm / maps, maximum, maximum));
}
```

- [ ] **Step 4: Re-run the focused tests and verify they pass**

Run: `./gradlew.bat :fabric-1.21.11:test --tests '*ViewfinderSessionTest' --tests '*ViewfinderOverlayTest' --no-daemon --console=plain`

Expected: PASS.

### Task 2: Wire preflight selection through the client UI and capture flow

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderControlsScreen.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/PreviewScreen.java`
- Modify: `fabric/src/main/resources/assets/tobyscamera/lang/en_us.json`
- Modify: `fabric/src/main/resources/assets/tobyscamera/lang/zh_cn.json`

- [ ] **Step 1: Write a failing session-flow test**

```java
@Test
void shutterUsesTheSelectedPrintSize() {
    ViewfinderSession session = new ViewfinderSession();
    session.open();
    session.setPrintSize(3, 6);
    assertTrue(session.pressShutter());
    assertEquals(3, session.gridSize());
}
```

- [ ] **Step 2: Run it and verify the test fails**

Run: `./gradlew.bat :fabric-1.21.11:test --tests '*ViewfinderSessionTest.shutterUsesTheSelectedPrintSize' --no-daemon --console=plain`

Expected: compilation failure because the no-argument `pressShutter()` does not exist.

- [ ] **Step 3: Implement the selected-size capture flow and concise HUD**

```java
public boolean pressShutter() {
    return pressShutter(printSize);
}
```

Persist the selected print size in `ViewfinderSettingsStore`; pass `heldCameraGridSize` to the controls screen and use it to populate a `Print size` cycle control. Keep capture at the camera maximum so preview can still offer all valid lower resolutions. Construct `PreviewScreen` with the persisted default size, keep its resolution `CycleButton`, and do not write preview selection back to `ViewfinderSession`. Render the selected default layout and film/capacity/remaining-shot readouts in the overlay, and remove the status/mode label rendering.

- [ ] **Step 4: Re-run the focused tests**

Run: `./gradlew.bat :fabric-1.21.11:test --tests '*ViewfinderSessionTest' --tests '*ViewfinderOverlayTest' --no-daemon --console=plain`

Expected: PASS.

### Task 3: Verify the module and published artifact

**Files:**
- Verify only

- [ ] **Step 1: Run Fabric target tests and packaging verification**

Run: `./gradlew.bat :fabric-1.21.11:test :fabric-1.21.11:buildAndCollect :fabric-1.21.11:verifyPublishedJar --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect the final diff**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only the source, resource, test, spec, and plan files listed above are modified.
