# Static Map Render Deduplication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Skip camera-map canvas writes when an unchanged tile has already been painted on that canvas.

**Architecture:** `TileMapRenderer` assigns a revision to its current immutable pixel array and remembers the revision last written to each weakly referenced `MapCanvas`. Pixel replacement or clearing invalidates each canvas's prior revision.

**Tech Stack:** Java 21, Paper `MapRenderer`/`MapCanvas`, JUnit 5, Mockito.

---

### Task 1: Specify canvas-local render invalidation

**Files:**
- Modify: `folia/src/test/java/dev/tobyscamera/folia/map/TileMapRendererTest.java`

- [ ] **Step 1: Add failing deduplication tests**

```java
@Test
void skipsCanvasWritesWhenPixelsAreUnchanged() {
    TileMapRenderer renderer = new TileMapRenderer(new byte[16_384]);
    MapCanvas canvas = mock(MapCanvas.class);
    renderer.render(null, canvas, null);
    renderer.render(null, canvas, null);
    verify(canvas, times(16_384)).setPixel(anyInt(), anyInt(), anyByte());
}

@Test
void repaintsChangedPixelsAndEachNewCanvas() {
    TileMapRenderer renderer = new TileMapRenderer(new byte[16_384]);
    MapCanvas first = mock(MapCanvas.class); MapCanvas second = mock(MapCanvas.class);
    renderer.render(null, first, null); renderer.render(null, second, null);
    renderer.setPixels(new byte[16_384]); renderer.render(null, first, null);
    verify(first, times(32_768)).setPixel(anyInt(), anyInt(), anyByte());
    verify(second, times(16_384)).setPixel(anyInt(), anyInt(), anyByte());
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.TileMapRendererTest`

Expected: FAIL because unchanged renders currently make 32,768 writes.

### Task 2: Deduplicate unchanged canvas paints

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/TileMapRenderer.java`

- [ ] **Step 1: Add a revision and weak canvas-revision map**

```java
private final Map<MapCanvas, Long> renderedRevisions = Collections.synchronizedMap(new WeakHashMap<>());
private long revision;
```

- [ ] **Step 2: Increment the revision in `setPixels` and `clearPixels`, then short-circuit render only when the canvas has that revision**

```java
synchronized (this) {
    current = pixels;
    currentRevision = revision;
    if (Long.valueOf(currentRevision).equals(renderedRevisions.get(canvas))) return;
    renderedRevisions.put(canvas, currentRevision);
}
```

- [ ] **Step 3: Run focused tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.TileMapRendererTest --tests dev.tobyscamera.folia.map.StillMapAttachmentServiceTest`

Expected: PASS.

### Task 3: Verify and commit

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/TileMapRenderer.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/map/TileMapRendererTest.java`

- [ ] **Step 1: Run the Folia test suite**

Run: `./gradlew.bat :folia:test`

Expected: PASS.

- [ ] **Step 2: Commit the implementation**

```powershell
git add -- folia/src/main/java/dev/tobyscamera/folia/map/TileMapRenderer.java folia/src/test/java/dev/tobyscamera/folia/map/TileMapRendererTest.java docs/superpowers/plans/2026-07-19-static-map-render-deduplication.md
git commit -m "perf: skip unchanged static map paints"
```

