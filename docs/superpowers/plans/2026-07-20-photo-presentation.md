# Photo Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow each uploaded photo to have a transient name and description plus two persistent visibility defaults, and preserve the resulting presentation on bags, maps, copies, placement recovery, and unpacking.

**Architecture:** Add a shared `PhotoPresentation` value to the upload-begin protocol. The Fabric preview builds it from two transient fields and the persisted visibility preferences. The Folia upload coordinator attaches it to server-captured metadata, and all item factories persist/read it from root custom data before rendering player-visible names and lore.

**Tech Stack:** Java 21, Fabric client GUI, shared binary packet codec, Paper/Folia item components, JUnit 5, Gradle.

---

### Task 1: Define and transport presentation metadata

**Files:**
- Create: `common/src/main/java/dev/tobyscamera/common/protocol/PhotoPresentation.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/Packets.java`
- Modify: `common/src/main/java/dev/tobyscamera/common/protocol/PacketCodec.java`
- Modify: `common/src/test/java/dev/tobyscamera/common/protocol/PacketCodecTest.java`

- [ ] **Step 1: Write failing codec tests for presentation-aware upload begin.**

Add an `UploadBegin` using `new PhotoPresentation("晨雾", "山谷日出", false, true)`, assert it round-trips, assert blank padded text normalizes to empty, and assert a 513-byte UTF-8 name/description is rejected.

- [ ] **Step 2: Run the focused test to verify it fails.**

Run: `./gradlew.bat :common:test --tests dev.tobyscamera.common.protocol.PacketCodecTest --no-daemon`

Expected: compilation failure because `PhotoPresentation` and the extended `UploadBegin` constructor do not exist.

- [ ] **Step 3: Add the immutable presentation value and packet encoding.**

Create `PhotoPresentation` with `String name`, `String description`, `boolean publicAddress`, and `boolean publicPhotographer`; trim both strings and supply `DEFAULT = new PhotoPresentation("", "", true, true)`. Extend `UploadBegin` to carry it. Bump `PacketCodec.VERSION`, encode/decode name, description, and both booleans following width/height, and reject text above the existing 512 UTF-8-byte boundary.

- [ ] **Step 4: Run the focused test to verify it passes.**

Run: `./gradlew.bat :common:test --tests dev.tobyscamera.common.protocol.PacketCodecTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the protocol unit.**

Run: `git add common/src/main/java/dev/tobyscamera/common/protocol common/src/test/java/dev/tobyscamera/common/protocol/PacketCodecTest.java && git commit -m "feat: carry photo presentation in uploads"`

### Task 2: Persist visibility defaults and submit preview presentation

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSettings.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSettingsStore.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSession.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/viewfinder/PreviewScreen.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/camera/PhotoUploadController.java`
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/TobysCameraClient.java`
- Modify: `fabric/src/main/resources/assets/tobyscamera/lang/zh_cn.json`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/viewfinder/ViewfinderSettingsStoreTest.java`
- Modify: `fabric/src/test/java/dev/tobyscamera/fabric/camera/PhotoUploadControllerTest.java`

- [ ] **Step 1: Write failing tests for visibility-only client persistence and upload begin forwarding.**

Extend the settings-store test to construct settings with `publicAddress=false` and `publicPhotographer=true`, save/load it, and assert the properties file contains only these booleans in addition to existing settings. Add a controller test whose fake sender asserts that `confirm(photo, preview, new PhotoPresentation(...))` emits an `UploadBegin` carrying that exact presentation.

- [ ] **Step 2: Run focused client tests to verify they fail.**

Run: `./gradlew.bat :fabric-1.21.11:test --tests dev.tobyscamera.fabric.viewfinder.ViewfinderSettingsStoreTest --tests dev.tobyscamera.fabric.camera.PhotoUploadControllerTest --no-daemon`

Expected: compilation failure because settings and `confirm` lack presentation fields.

- [ ] **Step 3: Implement persisted defaults and the preview controls.**

Add `publicAddress` and `publicPhotographer` to `ViewfinderSettings` with true defaults; read missing properties as true and save both fields. Expose update methods on `ViewfinderSession` that notify the existing deferred settings writer. In `PreviewScreen`, add title and description `EditBox` values initialized empty plus two localized toggle controls initialized from the session. When `Use photo` is clicked, construct `PhotoPresentation` from the fields and toggles and pass it to the consumer; retake/close discards the fields. Update `PhotoUploadController.confirm` to send the presentation in `UploadBegin`, update the client callback to provide the session's preference values, and add Chinese translations.

- [ ] **Step 4: Run focused client tests to verify they pass.**

Run: `./gradlew.bat :fabric-1.21.11:test --tests dev.tobyscamera.fabric.viewfinder.ViewfinderSettingsStoreTest --tests dev.tobyscamera.fabric.camera.PhotoUploadControllerTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the client unit.**

Run: `git add fabric/src/main/java fabric/src/main/resources/assets/tobyscamera/lang/zh_cn.json fabric/src/test/java/dev/tobyscamera/fabric && git commit -m "feat: collect photo presentation in preview"`

### Task 3: Preserve presentation while creating, copying, recovering, and unpacking items

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/PhotoMetadata.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagFactory.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapItemPresentation.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/bag/PhotoBagFactoryTest.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/map/MapItemPresentationTest.java`

- [ ] **Step 1: Write failing server presentation and lifecycle tests.**

Create metadata with `new PhotoPresentation("旅行回忆", "第一天", false, true)`. Assert bag lore has `第一天` and photographer but not coordinate, and uses `旅行回忆` as its display name. Clone and read the bag, then rebuild it and assert the rebuilt bag retains the same presentation. In `MapItemPresentationTest`, assert an extracted-map presentation has the same custom name, description, and visibility behavior. Add the corresponding address-visible/photographer-hidden assertion.

- [ ] **Step 2: Run focused server tests to verify they fail.**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.bag.PhotoBagFactoryTest --tests dev.tobyscamera.folia.map.MapItemPresentationTest --no-daemon`

Expected: compilation failure because server metadata has no presentation and presenters always expose both fields.

- [ ] **Step 3: Implement metadata propagation and item rendering.**

Attach `PhotoPresentation` to captured `PhotoMetadata`; on upload begin, combine the presentation with the captured metadata and retain it per token. Write/read its name, description, and visibility flags in `PhotoBagFactory` root custom data, treating missing keys as legacy defaults. Use it in `PhotoBagFactory` and `MapItemPresentation` to select names and include only allowed lore lines. Persist the same metadata when marking placed maps and creating extracted maps so item-frame recovery and unpacking call their existing reconstruction paths with the original presentation.

- [ ] **Step 4: Run focused server tests to verify they pass.**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.bag.PhotoBagFactoryTest --tests dev.tobyscamera.folia.map.MapItemPresentationTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the server unit.**

Run: `git add folia/src/main/java/dev/tobyscamera/folia folia/src/test/java/dev/tobyscamera/folia && git commit -m "feat: preserve photo presentation on items"`

### Task 4: Verify all supported builds and lifecycle regressions

**Files:**
- Modify only if required by a failing test from Tasks 1–3.

- [ ] **Step 1: Run all tests.**

Run: `./gradlew.bat test --no-daemon`

Expected: all common, Fabric 1.21.11, Fabric 26.1, and Folia tests PASS.

- [ ] **Step 2: Run static checks for user-visible requirements.**

Run: `rg -n 'description|publicAddress|publicPhotographer|photo_name' common/src fabric/src folia/src`

Expected: the protocol, client settings/UI, and both bag/map presentation paths reference the new fields; no production code writes them only to a client-local setting.

- [ ] **Step 3: Inspect the final worktree state.**

Run: `git status --short && git log --oneline main..HEAD`

Expected: only the intentional design/plan and feature commits are ahead of `main`.
