# Magic Photo Camera Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-only, film-free `tobyscamera:magic_photo` camera that removes one held item when a validated photo upload begins.

**Architecture:** `CameraFilmService` owns recognition and stack mutation for the new custom-data flag, including the film-free decision. `UploadCoordinator` chooses the magic-camera path after all existing validations succeed; ordinary cameras retain their film-charge path.

**Tech Stack:** Java 21, Paper/Folia API, JUnit 5, Mockito, Gradle.

---

## File structure

- `folia/src/main/java/dev/tobyscamera/folia/camera/CameraFilmService.java` — magic-photo key, predicate, film-free decision, and stack decrement.
- `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java` — invokes magic-camera consumption at the charge point.
- `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java` — verifies accepted and rejected magic uploads.
- `README.md` — explains the item tag and server-only behavior.

### Task 1: Test and implement the magic-camera boundary

**Files:**
- Modify: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/camera/CameraFilmService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`

- [x] **Step 1: Write failing coordinator tests**

Add these methods to `UploadCoordinatorTest`:

```java
@Test
void beginConsumesMagicPhotoCameraThenGrantsToken() {
    Player player = player(); List<CameraPacket> sent = new ArrayList<>();
    CameraFilmService films = mock(CameraFilmService.class); ItemStack camera = mock(ItemStack.class);
    when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(1);
    when(films.isMagicPhoto(camera)).thenReturn(true); when(films.consumeMagicPhoto(camera)).thenReturn(true);
    UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });
    coordinator.handle(player, new Packets.UploadBegin(1, 1));
    assertEquals(Packets.UploadGranted.class, sent.getFirst().getClass());
    verify(films).consumeMagicPhoto(camera);
    org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
}

@Test
void rejectedMagicPhotoUploadDoesNotConsumeCamera() {
    Player player = player(); List<CameraPacket> sent = new ArrayList<>();
    CameraFilmService films = mock(CameraFilmService.class); ItemStack camera = mock(ItemStack.class);
    when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(1);
    when(films.isMagicPhoto(camera)).thenReturn(true);
    UploadCoordinator coordinator = new UploadCoordinator(PluginSettings.from(java.util.Map.of("upload.max-active-upload-bytes", 16_384L)), films,
            (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { }, ignored -> { });
    coordinator.handle(player, new Packets.UploadBegin(1, 1));
    assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
    org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consumeMagicPhoto(camera);
    org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
}
```

- [x] **Step 2: Run the test and observe the expected failure**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --no-daemon --console=plain`

Expected: Java compilation fails because `CameraFilmService` has no `isMagicPhoto(ItemStack)` or `consumeMagicPhoto(ItemStack)`.

- [x] **Step 3: Add the minimal service and coordinator implementation**

In `CameraFilmService`, define and initialize `magicPhotoKey` immediately after `noFilmRequiredKey`; add:

```java
public boolean isMagicPhoto(ItemStack camera) {
    return isCamera(camera) && RootCustomData.contains(camera, magicPhotoKey);
}

public boolean isFilmFree(ItemStack camera) {
    return isCamera(camera) && (RootCustomData.contains(camera, noFilmRequiredKey) || isMagicPhoto(camera));
}

public boolean consumeMagicPhoto(ItemStack camera) {
    if (!isMagicPhoto(camera) || camera.getAmount() < 1) return false;
    camera.setAmount(camera.getAmount() - 1);
    return true;
}
```

In `UploadCoordinator.begin`, replace the direct film-consumption conditional after `filmCost` with:

```java
if (films.isMagicPhoto(camera)) {
    if (!films.consumeMagicPhoto(camera)) {
        sender.send(player, new Packets.UploadRejected("Magic photo camera is no longer available"));
        return;
    }
} else if (!films.consume(camera, filmCost)) {
    sender.send(player, new Packets.UploadRejected("Camera does not have enough film"));
    return;
}
```

- [x] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL` and all `UploadCoordinatorTest` methods pass.

- [x] **Step 5: Commit the behavior and tests**

```bash
git add folia/src/main/java/dev/tobyscamera/folia/camera/CameraFilmService.java folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java
git commit -m "feat: add single-use magic photo camera"
```

### Task 2: Document and fully verify the feature

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-18-magic-photo-camera.md`

- [x] **Step 1: Add the operator documentation**

Add this section after the existing film-free camera text in `README.md`:

```markdown
### Magic photo camera

A camera tagged with `tobyscamera:magic_photo` is film-free but can be used for only one valid photo upload. The Folia server removes one held magic camera as soon as it accepts that upload; failed validation does not consume it. The behavior is server-side only, so no client mod update or extra item component is required.
```

- [x] **Step 2: Run the complete test suite**

Run: `./gradlew.bat test --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL` across `common`, `fabric`, and `folia`.

- [x] **Step 3: Inspect and commit the documentation**

Run: `git diff --check; git status --short`

Expected: no whitespace errors; the README and this plan are the only uncommitted changes.

```bash
git add README.md docs/superpowers/plans/2026-07-18-magic-photo-camera.md
git commit -m "docs: describe magic photo cameras"
```
