# Paper and Folia Plugin Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one TobysCamera plugin JAR operate correctly on Paper 1.21.11 and Folia 1.21.11.

**Architecture:** Introduce a small `ServerTaskScheduler` owned by the plugin.  A Paper implementation maps every operation to Bukkit's scheduler, while a Folia implementation preserves global, entity, async, and region ownership.  Plugin services receive this abstraction and no longer invoke Folia scheduler APIs directly.

**Tech Stack:** Java 21, Paper 1.21.11 API, Folia 1.21.11 API, Bukkit scheduler, JUnit 5, Mockito, Gradle.

---

## File structure

- `folia/src/main/java/dev/tobyscamera/folia/scheduler/ServerTaskScheduler.java`: scheduling contract and cancellation handle.
- `folia/src/main/java/dev/tobyscamera/folia/scheduler/PaperTaskScheduler.java`: Bukkit Scheduler implementation.
- `folia/src/main/java/dev/tobyscamera/folia/scheduler/FoliaTaskScheduler.java`: Folia ownership-aware implementation.
- `folia/src/main/java/dev/tobyscamera/folia/scheduler/ServerTaskSchedulers.java`: Folia detection and adapter selection.
- `folia/src/test/java/dev/tobyscamera/folia/scheduler/PaperTaskSchedulerTest.java`: Paper scheduling behavior tests.
- `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`: lifecycle, media callbacks, and cancellation migration.
- `folia/src/main/java/dev/tobyscamera/folia/net/PluginPayloadGateway.java`: packet routing migration.
- `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagPlacementListener.java`: delayed player work and preview restoration migration.
- `folia/src/main/java/dev/tobyscamera/folia/video/MapUpdateDispatcher.java`: player delivery migration.
- `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackService.java`: region indexing, player refresh, and async loading migration.
- `folia/build.gradle.kts`, `README.md`: normalized plugin filename and Paper/Folia installation documentation.

### Task 1: Establish the scheduler contract and Paper implementation

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/scheduler/ServerTaskScheduler.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/scheduler/PaperTaskScheduler.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/scheduler/PaperTaskSchedulerTest.java`

- [ ] **Step 1: Write failing Paper scheduling tests**

```java
@Test
void runsEntityWorkOnBukkitScheduler() {
    scheduler.runEntity(player, executed::incrementAndGet, retired::incrementAndGet);
    verify(bukkitScheduler).runTask(plugin, runnable.capture());
    runnable.getValue().run();
    assertEquals(1, executed.get());
    assertEquals(0, retired.get());
}

@Test
void repeatsGlobalWorkAndCancelsItsBukkitTask() {
    when(bukkitScheduler.runTaskTimer(plugin, task, 1L, 1L)).thenReturn(taskHandle);
    ServerTaskScheduler.TaskHandle handle = scheduler.runGlobalRepeating(1L, 1L, task);
    handle.cancel();
    verify(taskHandle).cancel();
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat :folia:test --tests '*PaperTaskSchedulerTest' --no-daemon`

Expected: compilation fails because `PaperTaskScheduler` and `ServerTaskScheduler` do not exist.

- [ ] **Step 3: Add the minimal contract and Paper adapter**

```java
public interface ServerTaskScheduler {
    void runGlobal(Runnable task);
    TaskHandle runGlobalRepeating(long delayTicks, long periodTicks, Runnable task);
    void runAsync(Runnable task);
    void runEntity(Player player, Runnable task, Runnable retired);
    void runEntityDelayed(Player player, long delayTicks, Runnable task, Runnable retired);
    void runRegion(World world, int chunkX, int chunkZ, Runnable task);
    interface TaskHandle { void cancel(); }
}
```

`PaperTaskScheduler` delegates global, entity, and region work to `BukkitScheduler#runTask`; delays to `runTaskLater`; repetition to `runTaskTimer`; and persistence/frame-loading work to `runTaskAsynchronously`.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew.bat :folia:test --tests '*PaperTaskSchedulerTest' --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the tested Paper scheduler**

```powershell
git add folia/src/main/java/dev/tobyscamera/folia/scheduler folia/src/test/java/dev/tobyscamera/folia/scheduler
git commit -m "feat: add Paper scheduler adapter"
```

### Task 2: Add Folia selection without a Paper linkage path

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/scheduler/FoliaTaskScheduler.java`
- Create: `folia/src/main/java/dev/tobyscamera/folia/scheduler/ServerTaskSchedulers.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/scheduler/ServerTaskSchedulersTest.java`

- [ ] **Step 1: Write failing runtime-selection tests**

```java
@Test
void selectsPaperWhenTheFoliaRuntimeMarkerIsAbsent() {
    assertFalse(ServerTaskSchedulers.isFolia(getClass().getClassLoader()));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat :folia:test --tests '*ServerTaskSchedulersTest' --no-daemon`

Expected: compilation fails because `ServerTaskSchedulers` does not exist.

- [ ] **Step 3: Implement detection and Folia adapter**

```java
static boolean isFolia(ClassLoader loader) {
    try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, loader);
        return true;
    } catch (ClassNotFoundException ignored) {
        return false;
    }
}

static ServerTaskScheduler create(Plugin plugin) {
    return isFolia(plugin.getClass().getClassLoader())
            ? new FoliaTaskScheduler(plugin)
            : new PaperTaskScheduler(plugin);
}
```

`FoliaTaskScheduler` uses global region, async, player entity, and region schedulers exactly where the existing source does; its periodic task handle delegates to Folia's `ScheduledTask#cancel`.

- [ ] **Step 4: Run focused tests and compile the Folia implementation**

Run: `./gradlew.bat :folia:test --tests '*ServerTaskSchedulersTest' --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit runtime selection**

```powershell
git add folia/src/main/java/dev/tobyscamera/folia/scheduler folia/src/test/java/dev/tobyscamera/folia/scheduler
git commit -m "feat: select Paper or Folia scheduler at runtime"
```

### Task 3: Migrate plugin lifecycle, uploads, and listeners

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/net/PluginPayloadGateway.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagPlacementListener.java`

- [ ] **Step 1: Write a failing constructor-injection test for the payload gateway**

```java
@Test
void dispatchesDecodedPacketsThroughTheProvidedScheduler() {
    gateway.onPluginMessageReceived(CHANNEL, player, PacketCodec.encode(new Packets.UploadBegin(1, 1)));
    verify(scheduler).runEntity(eq(player), runnable.capture(), any());
    runnable.getValue().run();
    verify(photoCoordinator).handle(eq(player), any(Packets.UploadBegin.class));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat :folia:test --tests '*PluginPayloadGatewayTest' --no-daemon`

Expected: the new constructor parameter is not available.

- [ ] **Step 3: Inject `ServerTaskScheduler` and replace direct calls**

Create the scheduler in `onEnable`, pass it to the gateway, bag listener, and video services, and store periodic tasks as `ServerTaskScheduler.TaskHandle`.  Replace each `getGlobalRegionScheduler`, `getAsyncScheduler`, and `player.getScheduler` invocation with the matching `runGlobal`, `runAsync`, `runEntity`, or `runEntityDelayed` operation.  Preserve the current retired-player fallback that queues photo delivery.

- [ ] **Step 4: Run the migrated gateway and upload tests**

Run: `./gradlew.bat :folia:test --tests '*PluginPayloadGatewayTest' --tests '*UploadCoordinatorTest' --tests '*VideoUploadCoordinatorTest' --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit lifecycle migration**

```powershell
git add folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/main/java/dev/tobyscamera/folia/net/PluginPayloadGateway.java folia/src/main/java/dev/tobyscamera/folia/bag/PhotoBagPlacementListener.java folia/src/test/java
git commit -m "refactor: route plugin work through server scheduler"
```

### Task 4: Migrate video playback ownership

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/video/MapUpdateDispatcher.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/video/VideoPlaybackService.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/video/MapUpdateDispatcherTest.java`

- [ ] **Step 1: Write a failing dispatcher test**

```java
@Test
void sendsAChangedMapOnEachViewerEntityScheduler() {
    dispatcher.send(map, List.of(player));
    verify(scheduler).runEntity(eq(player), runnable.capture(), any());
    runnable.getValue().run();
    verify(player).sendMap(map);
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat :folia:test --tests '*MapUpdateDispatcherTest' --no-daemon`

Expected: `MapUpdateDispatcher` still requires an `EntityScheduler` viewer.

- [ ] **Step 3: Remove exposed Folia scheduler types**

Make `MapUpdateDispatcher` own `ServerTaskScheduler` and accept `Collection<Player>`.  Make `VideoPlaybackService` use `runRegion` when indexing loaded chunks, `runAsync` for frame preload, and `runEntityDelayed` for held-map refresh.  Store player snapshots without `EntityScheduler` handles and return players from `viewers`.

- [ ] **Step 4: Run focused playback tests**

Run: `./gradlew.bat :folia:test --tests '*MapUpdateDispatcherTest' --tests '*VideoPlayback*Test' --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit video scheduler migration**

```powershell
git add folia/src/main/java/dev/tobyscamera/folia/video folia/src/test/java/dev/tobyscamera/folia/video
git commit -m "refactor: support Paper video playback scheduling"
```

### Task 5: Normalize distribution and verify both targets

**Files:**
- Modify: `folia/build.gradle.kts`
- Modify: `README.md`

- [ ] **Step 1: Write a failing artifact-name assertion in the build verification script**

Add `tobyscamera-plugin-<mod_version>.jar` as the expected server artifact in the existing module verification task.

- [ ] **Step 2: Run the artifact verification and verify RED**

Run: `./gradlew.bat :folia:jar --no-daemon`

Expected: the produced filename remains `tobyscamera-folia-<mod_version>.jar`.

- [ ] **Step 3: Rename the artifact and update documentation**

```kotlin
tasks.jar {
    archiveFileName.set("tobyscamera-plugin-${rootProject.version}.jar")
}
```

Document the single plugin JAR as supported on both Paper and Folia, and change the manual verification section to run the same workflow on each server type.

- [ ] **Step 4: Run build and JAR checks**

Run: `./gradlew.bat build verifyModules :folia:jar --no-daemon`

Expected: `BUILD SUCCESSFUL`; `folia/build/libs/tobyscamera-plugin-<mod_version>.jar` contains `plugin.yml` and `config.yml`.

- [ ] **Step 5: Manually smoke-test the same JAR**

Run: `./gradlew.bat runServer --no-daemon`

Expected: Paper enables `TobysCamera` without a linkage error.  Then install the generated JAR in a Folia 1.21.11 test server and repeat photo/video upload, map delivery, and placed-map playback checks.

- [ ] **Step 6: Commit distribution changes**

```powershell
git add folia/build.gradle.kts README.md build.gradle.kts
git commit -m "build: publish Paper and Folia plugin artifact"
```
