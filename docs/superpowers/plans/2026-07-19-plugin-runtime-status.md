# Plugin Runtime Status Implementation Plan

> For agentic workers: execute task-by-task using superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Add /tobyscamera status with live upload/render data, current-run upload totals, and stored-photo totals.

**Architecture:** Live services expose synchronized immutable snapshots. A plugin-owned counter initializes once from SQLite and updates only after persistence succeeds. The command combines only in-memory snapshots.

**Tech Stack:** Java 21, Paper/Folia API, SQLite JDBC, JUnit 5, Mockito.

---

### Task 1: Add a stored-photo aggregate

**Files:**
- Create: folia/src/main/java/dev/tobyscamera/folia/storage/PhotoStorageStats.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRepository.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java
- Modify: folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java

- [ ] **Step 1: Write the failing aggregate test**

Add this test to SqlitePhotoRepositoryTest:

~~~java
@Test
void reportsPhotoAndTileTotalsWithoutLoadingPayloads() throws Exception {
    try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
        assertEquals(new PhotoStorageStats(0, 0), repository.stats());
        repository.save(record(2, 1), pixels(2), filled((byte) 1));
        repository.save(record(1, 3), pixels(3), filled((byte) 2));
        assertEquals(new PhotoStorageStats(2, 5), repository.stats());
    }
}
~~~

Add test helpers that create a PhotoRecord whose mapIds cover every coordinate and a matching LinkedHashMap<TileCoordinate, byte[]> of 16,384-byte tiles.

- [ ] **Step 2: Run the test to prove the API is missing**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.SqlitePhotoRepositoryTest.reportsPhotoAndTileTotalsWithoutLoadingPayloads

Expected: compilation fails because PhotoStorageStats and PhotoRepository.stats() do not exist.

- [ ] **Step 3: Implement the minimal aggregate API**

Create PhotoStorageStats.java:

~~~java
package dev.tobyscamera.folia.storage;

public record PhotoStorageStats(long photoCount, long tileCount) {
    public PhotoStorageStats {
        if (photoCount < 0 || tileCount < 0) {
            throw new IllegalArgumentException("storage totals must be non-negative");
        }
    }
}
~~~

Add this to PhotoRepository before close():

~~~java
PhotoStorageStats stats() throws IOException;
~~~

Add this to SqlitePhotoRepository before close():

~~~java
@Override
public synchronized PhotoStorageStats stats() throws IOException {
    try (Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery(
                 "select count(*), coalesce(sum(width * height), 0) from photos")) {
        if (!result.next()) return new PhotoStorageStats(0, 0);
        return new PhotoStorageStats(result.getLong(1), result.getLong(2));
    } catch (SQLException exception) {
        throw new IOException("could not count stored photos", exception);
    }
}
~~~

- [ ] **Step 4: Run the focused storage suite**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.storage.SqlitePhotoRepositoryTest

Expected: PASS.

- [ ] **Step 5: Commit the aggregate**

~~~powershell
git add folia/src/main/java/dev/tobyscamera/folia/storage/PhotoStorageStats.java folia/src/main/java/dev/tobyscamera/folia/storage/PhotoRepository.java folia/src/main/java/dev/tobyscamera/folia/storage/SqlitePhotoRepository.java folia/src/test/java/dev/tobyscamera/folia/storage/SqlitePhotoRepositoryTest.java
git commit -m "feat: expose stored photo totals"
~~~

### Task 2: Expose current upload and render snapshots

**Files:**
- Modify: folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java
- Modify: folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/map/VirtualStillMapService.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java
- Modify: folia/src/test/java/dev/tobyscamera/folia/map/VirtualStillMapServiceTest.java

- [ ] **Step 1: Write failing snapshot tests**

Add this test to UploadCoordinatorTest:

~~~java
@Test
void reportsActiveUploadsTheirDeclaredTilesAndReservedBytes() {
    Player player = player();
    List<CameraPacket> sent = new ArrayList<>();
    CameraFilmService films = mock(CameraFilmService.class);
    ItemStack camera = mock(ItemStack.class);
    when(films.heldCamera(player)).thenReturn(camera);
    when(films.maximumForFilm(camera, 4)).thenReturn(2);
    when(films.consume(camera, 4)).thenReturn(true);
    UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });

    coordinator.handle(player, new Packets.UploadBegin(2, 2));

    assertEquals(new UploadCoordinator.Status(1, 4, 81_920, 16_777_216), coordinator.status());
}
~~~

Add this test to VirtualStillMapServiceTest:

~~~java
@Test
void reportsDistinctPhotosAndActiveMapsThenDropsThemAfterLastDetach() {
    VirtualStillMapService service = new VirtualStillMapService(
            Runnable::run, Runnable::run, ignored -> { }, mock(VirtualMapPacketSender.class));
    Player player = mock(Player.class);
    UUID photoId = UUID.randomUUID();
    service.attach("a", player, new MediaMapDescriptor.PhotoTile(10, photoId, new TileCoordinate(0, 0)),
            () -> new byte[16_384]);
    service.attach("b", player, new MediaMapDescriptor.PhotoTile(11, photoId, new TileCoordinate(1, 0)),
            () -> new byte[16_384]);

    assertEquals(new VirtualStillMapService.Status(1, 2), service.status());
    service.detach("a");
    assertEquals(new VirtualStillMapService.Status(1, 1), service.status());
    service.detach("b");
    assertEquals(new VirtualStillMapService.Status(0, 0), service.status());
}
~~~

Add the required assertEquals and TileCoordinate imports.

- [ ] **Step 2: Verify both tests fail**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --tests dev.tobyscamera.folia.map.VirtualStillMapServiceTest

Expected: compilation fails because neither service has Status or status().

- [ ] **Step 3: Add immutable synchronized snapshots**

Add this record and method to UploadCoordinator:

~~~java
public record Status(int activePhotoCount, int activeTileCount,
        long reservedBytes, long maxReservedBytes) { }

public synchronized Status status() {
    int tiles = sessions.values().stream()
            .mapToInt(session -> session.width() * session.height())
            .sum();
    return new Status(sessions.size(), tiles, activeUploadBytes, settings.uploadMaxActiveBytes());
}
~~~

Add this record and method to VirtualStillMapService:

~~~java
public record Status(int activePhotoCount, int activeMapCount) { }

public synchronized Status status() {
    int photos = (int) byMapId.values().stream()
            .map(attachment -> attachment.mediaId)
            .distinct()
            .count();
    return new Status(photos, byMapId.size());
}
~~~

Change construction from new Attachment(descriptor.mapId()) to new Attachment(descriptor.mapId(), descriptor.mediaId()), and give Attachment a final UUID mediaId initialized by that constructor. Add this forwarding method to MediaMapActivationListener:

~~~java
public VirtualStillMapService.Status status() {
    return stills.status();
}
~~~

- [ ] **Step 4: Run the focused live-state suites**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.upload.UploadCoordinatorTest --tests dev.tobyscamera.folia.map.VirtualStillMapServiceTest

Expected: PASS.

- [ ] **Step 5: Commit the snapshot APIs**

~~~powershell
git add folia/src/main/java/dev/tobyscamera/folia/upload/UploadCoordinator.java folia/src/test/java/dev/tobyscamera/folia/upload/UploadCoordinatorTest.java folia/src/main/java/dev/tobyscamera/folia/map/VirtualStillMapService.java folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java folia/src/test/java/dev/tobyscamera/folia/map/VirtualStillMapServiceTest.java
git commit -m "feat: expose live camera runtime state"
~~~

### Task 3: Keep current-run and stored totals in memory

**Files:**
- Create: folia/src/main/java/dev/tobyscamera/folia/status/PluginRuntimeStatus.java
- Create: folia/src/test/java/dev/tobyscamera/folia/status/PluginRuntimeStatusTest.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java

- [ ] **Step 1: Write the failing counter test**

Create PluginRuntimeStatusTest.java:

~~~java
package dev.tobyscamera.folia.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.storage.PhotoStorageStats;
import org.junit.jupiter.api.Test;

class PluginRuntimeStatusTest {
    @Test
    void preservesLoadedStorageTotalsAndCountsOnlyPersistedPhotosInThisRun() {
        PluginRuntimeStatus status = new PluginRuntimeStatus(new PhotoStorageStats(8, 21));
        status.recordPersisted(4);
        status.recordPersisted(1);

        assertEquals(new PluginRuntimeStatus.Totals(2, 5, 10, 26), status.totals());
    }
}
~~~

- [ ] **Step 2: Verify it fails**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.status.PluginRuntimeStatusTest

Expected: compilation fails because PluginRuntimeStatus is missing.

- [ ] **Step 3: Implement the counter and persistence notification**

Create PluginRuntimeStatus.java:

~~~java
package dev.tobyscamera.folia.status;

import dev.tobyscamera.folia.storage.PhotoStorageStats;

public final class PluginRuntimeStatus {
    private long runPhotos;
    private long runTiles;
    private long storedPhotos;
    private long storedTiles;

    public PluginRuntimeStatus(PhotoStorageStats stored) {
        storedPhotos = stored.photoCount();
        storedTiles = stored.tileCount();
    }

    public synchronized void recordPersisted(int tileCount) {
        if (tileCount < 1) throw new IllegalArgumentException("tile count must be positive");
        runPhotos++;
        runTiles += tileCount;
        storedPhotos++;
        storedTiles += tileCount;
    }

    public synchronized Totals totals() {
        return new Totals(runPhotos, runTiles, storedPhotos, storedTiles);
    }

    public record Totals(long runPhotoCount, long runTileCount,
            long storedPhotoCount, long storedTileCount) { }
}
~~~

In TobysCameraPlugin, add a PluginRuntimeStatus runtimeStatus field. Initialize it in the existing repository try immediately after creating the repository:

~~~java
repository = new SqlitePhotoRepository(getDataFolder().toPath());
runtimeStatus = new PluginRuntimeStatus(repository.stats());
~~~

Immediately after successful photos.persist(record, session), before scheduling delivery, add:

~~~java
runtimeStatus.recordPersisted(record.mapIds().size());
~~~

- [ ] **Step 4: Run the counter suite**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.status.PluginRuntimeStatusTest

Expected: PASS.

- [ ] **Step 5: Commit the counter**

~~~powershell
git add folia/src/main/java/dev/tobyscamera/folia/status/PluginRuntimeStatus.java folia/src/test/java/dev/tobyscamera/folia/status/PluginRuntimeStatusTest.java folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java
git commit -m "feat: track camera runtime upload totals"
~~~

### Task 4: Add and route /tobyscamera status

**Files:**
- Create: folia/src/main/java/dev/tobyscamera/folia/status/PluginStatusCommand.java
- Create: folia/src/test/java/dev/tobyscamera/folia/status/PluginStatusCommandTest.java
- Modify: folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java
- Modify: folia/src/main/resources/plugin.yml

- [ ] **Step 1: Write failing status-command tests**

Create PluginStatusCommandTest.java:

~~~java
package dev.tobyscamera.folia.status;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.folia.map.VirtualStillMapService;
import dev.tobyscamera.folia.storage.PhotoStorageStats;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class PluginStatusCommandTest {
    @Test
    void sendsEveryStatusGroupToAnAuthorizedOperator() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("tobyscamera.status")).thenReturn(true);
        PluginRuntimeStatus totals = new PluginRuntimeStatus(new PhotoStorageStats(7, 12));
        totals.recordPersisted(4);
        PluginStatusCommand command = new PluginStatusCommand(totals,
                () -> new UploadCoordinator.Status(2, 5, 81_920, 1_000_000),
                () -> new VirtualStillMapService.Status(3, 6));

        assertTrue(command.execute(sender));

        verify(sender, org.mockito.Mockito.times(5))
                .sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void deniesAnUnauthorizedCaller() {
        CommandSender sender = mock(CommandSender.class);
        PluginStatusCommand command = new PluginStatusCommand(
                new PluginRuntimeStatus(new PhotoStorageStats(0, 0)),
                () -> new UploadCoordinator.Status(0, 0, 0, 1),
                () -> new VirtualStillMapService.Status(0, 0));

        assertTrue(command.execute(sender));

        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
~~~

- [ ] **Step 2: Verify the command test fails**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.status.PluginStatusCommandTest

Expected: compilation fails because PluginStatusCommand is missing.

- [ ] **Step 3: Implement the command controller**

Create PluginStatusCommand.java:

~~~java
package dev.tobyscamera.folia.status;

import dev.tobyscamera.folia.map.VirtualStillMapService;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class PluginStatusCommand {
    private final PluginRuntimeStatus totals;
    private final Supplier<UploadCoordinator.Status> uploads;
    private final Supplier<VirtualStillMapService.Status> rendering;

    public PluginStatusCommand(PluginRuntimeStatus totals, Supplier<UploadCoordinator.Status> uploads,
            Supplier<VirtualStillMapService.Status> rendering) {
        this.totals = totals;
        this.uploads = uploads;
        this.rendering = rendering;
    }

    public boolean execute(CommandSender sender) {
        if (!sender.hasPermission("tobyscamera.status")) {
            sender.sendMessage(Component.text("You do not have permission to view TobysCamera status."));
            return true;
        }
        var render = rendering.get();
        var upload = uploads.get();
        var stored = totals.totals();
        sender.sendMessage(Component.text("TobysCamera status"));
        sender.sendMessage(Component.text("Rendering: " + render.activePhotoCount()
                + " photos, " + render.activeMapCount() + " maps"));
        sender.sendMessage(Component.text("Uploading: " + upload.activePhotoCount()
                + " photos, " + upload.activeTileCount() + " tiles, "
                + upload.reservedBytes() + "/" + upload.maxReservedBytes() + " bytes"));
        sender.sendMessage(Component.text("This run: " + stored.runPhotoCount()
                + " photos, " + stored.runTileCount() + " tiles"));
        sender.sendMessage(Component.text("Stored: " + stored.storedPhotoCount()
                + " photos, " + stored.storedTileCount() + " tiles"));
        return true;
    }
}
~~~

In TobysCameraPlugin, add a PluginStatusCommand statusCommand field and, after mediaActivation is constructed, assign:

~~~java
statusCommand = new PluginStatusCommand(runtimeStatus, coordinator::status, mediaActivation::status);
~~~

Replace the first command-dispatch condition with:

~~~java
if (args.length != 1) return false;
if (args[0].equalsIgnoreCase("status")) return statusCommand.execute(sender);
if (!args[0].equalsIgnoreCase("reload")) return false;
~~~

Retain the existing reload permission and reload body.

Update plugin.yml exactly as follows:

~~~yaml
commands:
  tobyscamera:
    description: Reload TobysCamera or show its runtime status.
    usage: /tobyscamera <reload|status>
permissions:
  tobyscamera.reload:
    default: op
  tobyscamera.status:
    default: op
~~~

- [ ] **Step 4: Run the command tests and all Folia tests**

Run: ./gradlew.bat :folia:test --tests dev.tobyscamera.folia.status.PluginStatusCommandTest

Expected: PASS.

Run: ./gradlew.bat :folia:test

Expected: PASS with no failures.

- [ ] **Step 5: Commit the command**

~~~powershell
git add folia/src/main/java/dev/tobyscamera/folia/status/PluginStatusCommand.java folia/src/test/java/dev/tobyscamera/folia/status/PluginStatusCommandTest.java folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/main/resources/plugin.yml
git commit -m "feat: add camera runtime status command"
~~~

### Task 5: Package verification

**Files:**
- Modify: none

- [ ] **Step 1: Run the complete plugin build**

Run: ./gradlew.bat :folia:test :folia:jar

Expected: BUILD SUCCESSFUL and a plugin JAR under folia/build/libs/.

- [ ] **Step 2: Check the final diff**

Run: git diff --check HEAD

Expected: no output and exit code 0.

