# Server-Side Out-of-Order Photo Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Folia/Paper server accept occasional out-of-order preview, tile, and finish packets without changing the Fabric client or wire protocol.

**Architecture:** Replace contiguous offsets in `UploadSession` with fixed-size byte coverage bitmaps that accept safe unordered writes and idempotent duplicates. Record an early finish request in `UploadCoordinator` and complete once all media becomes covered, while preserving token ownership, TTL, rate limiting, and bounded admission memory.

**Tech Stack:** Java 21, JUnit 5, Mockito, Gradle, Paper/Folia API

---

### Task 1: Unordered media coverage

**Files:**
- Modify: `common/src/test/java/dev/tobyscamera/common/upload/UploadSessionTest.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/upload/UploadSession.java`

- [ ] **Step 1: Replace strict-order tests with failing unordered coverage tests**

Add tests that submit tile data before preview data, submit the `8_192` half before the `0` half, repeat identical overlapping bytes, reject conflicting overlap, and reject negative, empty, over-8,192-byte, and out-of-bounds ranges. Assert that `isComplete()` remains false until every preview and tile byte is covered.

```java
session.append(PLAYER, 0, 0, 8_192, second);
session.appendPreview(PLAYER, 8_192, second);
session.append(PLAYER, 0, 0, 0, first);
session.appendPreview(PLAYER, 0, first);
assertTrue(session.isComplete());

session.appendPreview(PLAYER, 0, first);
session.appendPreview(PLAYER, 4_096, Arrays.copyOfRange(first, 4_096, 8_192));

byte[] conflict = first.clone();
conflict[0] ^= 1;
assertThrows(UploadFailure.class,
        () -> session.appendPreview(PLAYER, 0, conflict));
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat :common:test --tests dev.tobyscamera.common.upload.UploadSessionTest --no-daemon`

Expected: FAIL because tile data currently requires a complete preview and reverse offsets currently throw `UploadFailure`.

- [ ] **Step 3: Implement fixed-memory unordered coverage**

In `UploadSession`, add:

```java
public static final int MAX_CHUNK_BYTES = 8_192;
public static final int COVERAGE_BYTES = TILE_BYTES / Byte.SIZE;
public static final int RESERVED_BYTES_PER_IMAGE = TILE_BYTES + COVERAGE_BYTES;

private final BitSet[] covered;
private final BitSet previewCovered = new BitSet(TILE_BYTES);
```

Initialize one `BitSet` per tile. Remove the preview-before-tile requirement and replace contiguous checks with a helper that:

```java
private static int appendRange(byte[] target, BitSet coverage, int offset, byte[] bytes) {
    if (offset < 0 || bytes.length < 1 || bytes.length > MAX_CHUNK_BYTES
            || offset > TILE_BYTES - bytes.length) {
        throw new UploadFailure("chunk range is invalid");
    }
    int end = offset + bytes.length;
    for (int index = coverage.nextSetBit(offset); index >= 0 && index < end;
            index = coverage.nextSetBit(index + 1)) {
        if (target[index] != bytes[index - offset]) {
            throw new UploadFailure("chunk overlaps conflicting data");
        }
    }
    int before = coverage.cardinality();
    System.arraycopy(bytes, 0, target, offset, bytes.length);
    coverage.set(offset, end);
    return coverage.cardinality() - before;
}
```

Update received byte counts only by the helper's newly covered count.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew.bat :common:test --tests dev.tobyscamera.common.upload.UploadSessionTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the session behavior**

```powershell
git add common/src/main/java/dev/tobyscamera/common/upload/UploadSession.java common/src/test/java/dev/tobyscamera/common/upload/UploadSessionTest.java
git commit -m "fix: accept out-of-order photo chunks"
```

### Task 2: Early finish completion

**Files:**
- Modify: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`

- [ ] **Step 1: Write a failing early-finish coordinator test**

Start a 1x1 upload, capture its granted token, send `UploadFinish` first, then send reversed tile halves before reversed preview halves. Assert no rejection or kick occurs and the completion handler receives the complete session exactly once.

```java
coordinator.handle(player, new Packets.UploadFinish(token));
coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 8_192, second));
coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 0, first));
coordinator.handle(player, new Packets.UploadPreviewChunk(token, 8_192, second));
coordinator.handle(player, new Packets.UploadPreviewChunk(token, 0, first));

assertEquals(session, completed.get());
verify(player, never()).kick(any());
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --no-daemon`

Expected: FAIL because early `UploadFinish` currently replies `UploadRejected` and later chunks do not trigger completion.

- [ ] **Step 3: Implement finish-request tracking**

Add a bounded-per-active-session set:

```java
private final Set<UUID> finishRequested = new HashSet<>();
```

On `UploadFinish`, add the token and call a shared completion helper without rejecting incomplete content. After each successful preview or tile append, call the same helper:

```java
private void completeIfReady(Player player, UUID token, UploadSession session) {
    if (!finishRequested.contains(token) || !session.isComplete()) return;
    PhotoMetadata metadata = uploadMetadata.get(token);
    clear(token);
    completionHandler.accept(player, session,
            metadata == null ? PhotoMetadata.capture(player) : metadata);
}
```

Remove the token from `finishRequested` in `clear()` so the set cannot outlive active session state.

- [ ] **Step 4: Run coordinator tests and verify GREEN**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit finish handling**

```powershell
git add folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java
git commit -m "fix: complete uploads after reordered finish"
```

### Task 3: Admission memory and regression verification

**Files:**
- Modify: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`

- [ ] **Step 1: Write a failing reserved-memory assertion**

For a 2x2 photo plus preview, assert five fixed image buffers reserve `5 * 18_432 = 92_160` bytes rather than only pixel bytes.

```java
assertEquals(new UploadCoordinator.Status(1, 4, 92_160, 16_777_216), coordinator.status());
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest.reportsActiveUploadsTheirDeclaredTilesAndReservedBytes --no-daemon`

Expected: FAIL with actual reserved bytes `81_920`.

- [ ] **Step 3: Include bitmap storage in admission accounting**

Calculate upload reservation with exact long arithmetic:

```java
long imageCount = Math.addExact(
        Math.multiplyExact((long) begin.gridWidth(), begin.gridHeight()), 1L);
uploadBytes = Math.multiplyExact(imageCount, UploadSession.RESERVED_BYTES_PER_IMAGE);
```

Keep the existing aggregate maximum check and rejection before film consumption.

- [ ] **Step 4: Run module regression tests**

Run: `./gradlew.bat :common:test :folia:test --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Confirm no client or protocol files changed**

Run: `git diff --name-only 1416c1a...HEAD`

Expected: only the design/plan, Common upload session, Folia coordinator, and their server-side tests; no path under `fabric/` and no protocol source file.

- [ ] **Step 6: Commit memory accounting and plan**

```powershell
git add docs/superpowers/plans/2026-07-22-server-out-of-order-photo-upload.md folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java
git commit -m "test: account for unordered upload coverage"
```
