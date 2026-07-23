# 管理员地图画图库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Folia/Paper 插件中提供可搜索、预览和管理已存储地图画的管理员箱子式图库。

**Architecture:** SQLite 仓库保存归属名称、拍摄 metadata、分页查询、上传禁用与媒体删除。图库 GUI 只保存管理员浏览状态，详情页按需通过现有虚拟地图服务显示一张预览，所有库存变更回到玩家所属线程。

**Tech Stack:** Java 21、Paper/Folia 1.21.11 API、SQLite JDBC、Adventure、JUnit 5、Mockito。

---

## 文件结构

- `folia/.../storage/PhotoRecord.java`：照片元数据、上传者名称和可空的 `PhotoMetadata`。
- `folia/.../storage/PhotoQuery.java`、`PhotoPage.java`、`UploadBlock.java`：查询、分页和禁用值对象。
- `folia/.../storage/PhotoRepository.java`、`SqlitePhotoRepository.java`：持久化、搜索、禁用和删除。
- `folia/.../gallery/PhotoGalleryState.java`：纯 Java 筛选/排序/分页状态。
- `folia/.../gallery/PhotoGalleryListener.java`：库存 GUI、聊天搜索和管理员操作。

### Task 1: 添加查询、metadata、上传禁用和删除的存储 API

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoQuery.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoPage.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/storage/UploadBlock.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRecord.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRepository.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java`

- [ ] **Step 1: 写入失败测试。**

```java
@Test void findsNewestMatchingPhotosAndPersistsMetadata() throws Exception {
    repository.save(record("Toby", metadata("A")), pixels(1, 1), preview());
    assertEquals("A", repository.findPage(new PhotoQuery("toby", Sort.NEWEST, 0, 45))
            .records().getFirst().metadata().presentation().name());
}
@Test void blocksAndUnblocksUploader() throws Exception {
    repository.block(new UploadBlock(playerId, adminId, Instant.now()));
    assertTrue(repository.isBlocked(playerId));
    repository.unblock(playerId);
    assertFalse(repository.isBlocked(playerId));
}
```

- [ ] **Step 2: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.SqlitePhotoRepositoryTest`，预期因新 API 缺失失败。**

- [ ] **Step 3: 实现最小存储模型和迁移。**

```java
public record PhotoQuery(String term, Sort sort, int page, int pageSize) {
    public enum Sort { NEWEST, OLDEST }
}
public record PhotoPage(List<PhotoRecord> records, boolean hasNext) { }
public record UploadBlock(UUID playerId, UUID adminId, Instant blockedAt) { }
```

Extend `PhotoRecord` with nullable `ownerName` and `PhotoMetadata`, retaining the old constructor. Add nullable `owner_name` via `ensure...Column`, add `photo_metadata(photo_id primary key, photographer, world, x, y, z, captured_at, name, description, public_...)`, and add `upload_blocks(player_id primary key, admin_id, blocked_at)`. Persist photo and metadata in one transaction. Implement parameterized case-insensitive name/UUID/photo-id filtering with `LIMIT pageSize + 1` and `OFFSET page * pageSize`, then expose `findPage`, `isBlocked`, `block`, `unblock`, and `delete`. Delete DB rows transactionally, delete only the resolved sharded `.tbc` container, and invalidate the photo tile/preview cache entries.

- [ ] **Step 4: 运行同一仓库测试，预期 PASS。**
- [ ] **Step 5: 提交：`git add folia/src/main/java/dev/tobyscamera/folia/storage folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java; git commit -m "feat: add photo gallery storage queries"`。**

### Task 2: 从记录恢复 metadata，并生成管理员不可复制非底片袋

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/delivery/MapDeliveryService.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/map/MapPhotoServiceTest.java`

- [ ] **Step 1: 写入失败测试。**

```java
@Test void adminBagIsPrintableButCannotBeCopied() {
    ItemStack bag = photos.adminBag(world, recordWithMetadata());
    assertFalse(PhotoBagFactory.isNegative(bag));
    assertTrue(PhotoBagFactory.isCopy(bag));
    assertEquals(recordWithMetadata().metadata(), PhotoBagFactory.read(bag).metadata());
}
```

- [ ] **Step 2: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MapPhotoServiceTest`，预期 `adminBag` 缺失而失败。**

- [ ] **Step 3: 实现记录驱动的袋创建。**

```java
public ItemStack originalBag(World world, PhotoRecord record) {
    return PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO,
            virtualMapIds.allocate(), record.gridWidth(), record.gridHeight(), record.metadata()));
}
public ItemStack adminBag(World world, PhotoRecord record) {
    return PhotoBagFactory.copyForPrint(originalBag(world, record));
}
```

Make `createMaps` receive player name and `PhotoMetadata`, persist both in `PhotoRecord`, and make normal and queued delivery restore `record.metadata()` instead of relying on the transient metadata map. Normal capture remains a negative; only `adminBag` invokes `copyForPrint`.

- [ ] **Step 4: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MapPhotoServiceTest --tests dev.tobyscamera.folia.bag.PhotoBagFactoryTest`，预期 PASS。**
- [ ] **Step 5: 提交：`git add folia/src/main/java/dev/tobyscamera/folia/map/MapPhotoService.java folia/src/main/java/dev/tobyscamera/folia/delivery/MapDeliveryService.java folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/test/java/dev/tobyscamera/folia; git commit -m "feat: restore photo metadata for gallery delivery"`。**

### Task 3: 只阻止被禁玩家的新上传

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java`

- [ ] **Step 1: 写入失败测试，断言被禁玩家的 `UploadBegin` 不消耗胶卷。**

```java
UploadCoordinator coordinator = coordinator(sent, films, completed, playerId -> true);
coordinator.handle(player, new Packets.UploadBegin(1, 1));
assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
verify(films, never()).consume(any(), anyInt());
```

- [ ] **Step 2: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest`，预期构造器不匹配而失败。**
- [ ] **Step 3: 向 `UploadCoordinator` 注入 `Predicate<UUID> isUploadBlocked`，保留使用 `ignored -> false` 的兼容构造器，仅在 `begin` 且权限检查后、相机和胶卷检查前拒绝。插件传入仓库查询；I/O 异常记录警告并仅拒绝当前新请求。绝不在 chunk 或 finish 中查询。**
- [ ] **Step 4: 运行同一上传测试，预期 PASS。**
- [ ] **Step 5: 提交：`git add folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java; git commit -m "feat: block disabled players from new uploads"`。**

### Task 4: 建立可测试的图库状态模型

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/gallery/PhotoGalleryState.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/gallery/PhotoGalleryStateTest.java`

- [ ] **Step 1: 写入失败测试。**

```java
PhotoGalleryState state = new PhotoGalleryState();
state.nextPage(); state.setTerm("Toby");
assertEquals(0, state.page());
state.nextPage(); state.toggleSort();
assertEquals(new PhotoQuery("Toby", Sort.OLDEST, 0, 45), state.query());
```

- [ ] **Step 2: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.gallery.PhotoGalleryStateTest`，预期状态类缺失而失败。**
- [ ] **Step 3: 添加无 Bukkit 依赖的状态类：固定页面大小 45；搜索或排序改变时归零页码；上一页不能小于零；查询值为当前 term、sort、page 和 pageSize。**
- [ ] **Step 4: 运行同一状态测试，预期 PASS。**
- [ ] **Step 5: 提交：`git add folia/src/main/java/dev/tobyscamera/folia/gallery/PhotoGalleryState.java folia/src/test/java/dev/tobyscamera/folia/gallery/PhotoGalleryStateTest.java; git commit -m "feat: add gallery navigation state"`。**

### Task 5: 实现 GUI、预览挂接和管理员操作

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/gallery/PhotoGalleryListener.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `folia/src/main/resources/plugin.yml`
- Test: `folia/src/test/java/dev/tobyscamera/folia/gallery/PhotoGalleryListenerTest.java`

- [ ] **Step 1: 写入失败测试，验证删除确认只删除当前选定记录且领取调用 `adminBag`。**

```java
gallery.openDetail(player, record);
gallery.deleteConfirmed(player);
verify(repository).delete(record.photoId());
verify(repository, never()).delete(other.photoId());
```

- [ ] **Step 2: 运行 `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.gallery.PhotoGalleryListenerTest`，预期监听器缺失而失败。**
- [ ] **Step 3: 实现监听器。**

Use per-player sessions and a custom `InventoryHolder`; cancel click, drag, double-click and number-key movement for every held GUI. Render a 6-row list (45 photos plus controls) and a details inventory with a single non-takeable preview, owner/date/size/ID, all stored metadata fields, public flags, delivery, block/unblock, back, close, and two-click delete confirmation. `AsyncChatEvent` search cancels the message then schedules state update. Repository queries, deletion and block writes run asynchronously; inventory mutation, preview attach and delivery run on `runEntity`.

Expose `MediaMapActivationListener.attachGalleryPreview(Player, String, ItemStack)` and `detachGalleryPreview(String)` around the existing virtual still service. Attach exactly one `PHOTO_BAG_PREVIEW` source while a details page is open; detach when closing, quitting, returning, replacing the selection, or deleting. Deliver `photos.adminBag(...)` using `MapItemDelivery` and the existing inventory/drop fallback. Require `tobyscamera.admin` for opening and every action. Register the listener; route `/tobyscamera gallery`; update the command usage.

- [ ] **Step 4: 运行同一图库测试，预期 PASS。**
- [ ] **Step 5: 提交：`git add folia/src/main/java/dev/tobyscamera/folia/gallery folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/main/resources/plugin.yml folia/src/test/java/dev/tobyscamera/folia/gallery; git commit -m "feat: add admin photo gallery"`。**

### Task 6: 验证和文档

**Files:**
- Modify: `README.md`
- Modify: `玩家使用手册.md`

- [ ] **Step 1: 在管理员章节说明 `/tobyscamera gallery`、筛选、metadata 预览、领取不可复制非底片袋、删除与上传禁用。**
- [ ] **Step 2: 运行 `./gradlew.bat :folia:test`，预期 PASS。**
- [ ] **Step 3: 运行 `./gradlew.bat verifyModules`，预期 PASS 且生成 Fabric/Folia 构件。**
- [ ] **Step 4: 运行 `git diff --check` 和 `git status --short`，只提交本任务文档：`git add README.md 玩家使用手册.md; git commit -m "docs: document admin photo gallery"`。**

## 自检

- 规格覆盖：任务 1 包含迁移、查询、禁用和删除；任务 2 覆盖 metadata 与管理员非底片不可复制袋；任务 3 仅拒绝新上传；任务 4-5 覆盖 GUI、搜索、排序、预览、确认删除、禁用和线程；任务 6 覆盖文档与全量验证。
- 占位扫描：没有 `TBD`、`TODO` 或未定义的后续工作。
- 类型一致性：后续任务统一使用 `PhotoQuery`、`PhotoPage`、`UploadBlock`、`PhotoRecord.metadata()` 和 `MapPhotoService.adminBag(...)`。
