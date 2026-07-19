# Virtual Map Delivery Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Progressively load and deliver virtual still-map pixels under per-player, global, and concurrent-read limits.

**Architecture:** A deterministic scheduler owns pending `(player UUID, virtual map ID)` work, prioritizes hand and nearby frame demands, and starts reads only when selected. `VirtualStillMapService` maintains source attachment lifetimes and delegates demand reconciliation to that scheduler. Plugin wiring ticks the scheduler globally and applies reloadable limits.

**Tech Stack:** Java 21, Paper/Folia scheduler adapter, Bukkit/Paper API, JUnit 5, Mockito.

---

### Task 1: Add validated delivery settings

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/config/PluginSettings.java`
- Modify: `folia/src/main/resources/config.yml`
- Create: `folia/src/test/java/dev/tobyscamera/folia/config/PluginSettingsTest.java`

- [ ] **Step 1: Write failing configuration tests**

```java
@Test void suppliesVirtualMapDeliveryDefaults() {
    PluginSettings settings = PluginSettings.from(Map.of());
    assertEquals(12, settings.virtualMapMaxConcurrentReads());
    assertEquals(4, settings.virtualMapPerPlayerMapsPerTick());
    assertEquals(65_536L, settings.virtualMapPerPlayerBytesPerTick());
    assertEquals(2_097_152L, settings.virtualMapGlobalBytesPerTick());
}

@Test void rejectsDeliveryLimitsThatCannotSendOneTile() {
    assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(Map.of(
        "virtual-map-delivery.per-player-bytes-per-tick", 16_383L)));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.config.PluginSettingsTest`

Expected: FAIL because the virtual-map delivery accessors do not exist.

- [ ] **Step 3: Implement the settings and resource defaults**

```java
// Add to PluginSettings record fields.
int virtualMapMaxConcurrentReads,
int virtualMapPerPlayerMapsPerTick,
long virtualMapPerPlayerBytesPerTick,
long virtualMapGlobalBytesPerTick

// Add to PluginSettings.from(Map):
integer(values, "virtual-map-delivery.max-concurrent-reads", 12),
integer(values, "virtual-map-delivery.per-player-maps-per-tick", 4),
longValue(values, "virtual-map-delivery.per-player-bytes-per-tick", 65_536L),
longValue(values, "virtual-map-delivery.global-bytes-per-tick", 2_097_152L)
```

Validate that all limits are positive and both byte limits are at least `16_384L`. Add the corresponding `virtual-map-delivery` YAML block with the values above.

- [ ] **Step 4: Run the focused settings test**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.config.PluginSettingsTest`

Expected: PASS.

### Task 2: Build the budgeted delivery scheduler with red-green tests

**Files:**
- Create: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualMapDeliveryScheduler.java`
- Create: `folia/src/test/java/dev/tobyscamera/folia/map/VirtualMapDeliverySchedulerTest.java`

- [ ] **Step 1: Write the failing scheduler tests**

```java
@Test void startsOnlyTheHighestPriorityDemandWhenOneReadSlotIsAvailable() { /* main hand before frame */ }
@Test void deduplicatesMultipleSourcesForTheSamePlayerAndMap() { /* one loader call and one send */ }
@Test void doesNotStartMoreReadsThanTheConfiguredLimit() { /* two demands, one slot */ }
@Test void appliesPerPlayerMapAndGlobalByteBudgetsPerTick() { /* 16 KiB tile assertions */ }
@Test void rotatesEqualPriorityPlayersBeforeStartingTheirSecondDemand() { /* A1, B1, A2 */ }
@Test void discardsAReadThatWasCancelledBeforeCompletion() { /* no sender invocation */ }
```

Use injected `Consumer<Runnable>` async/global queues and a fake sender to execute reads and completions deterministically; assertions must inspect calls to the real scheduler API rather than Mockito queue internals.

- [ ] **Step 2: Run the scheduler tests to verify failure**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VirtualMapDeliverySchedulerTest`

Expected: FAIL because `VirtualMapDeliveryScheduler` does not exist.

- [ ] **Step 3: Implement the minimal scheduler API and queue semantics**

```java
public final class VirtualMapDeliveryScheduler {
    public enum Priority { MAIN_HAND, OFF_HAND, FRAME }
    public record DemandKey(UUID playerId, int mapId) { }
    public record Limits(int maxConcurrentReads, int perPlayerMapsPerTick,
                         long perPlayerBytesPerTick, long globalBytesPerTick) { }

    public void attach(String source, Player player, int mapId, Priority priority,
                       long distanceSquared, PixelLoader loader) { }
    public void detach(String source) { }
    public void clear() { }
    public void tick() { }
}
```

Store each source attachment, merge it by `DemandKey`, and order candidates by priority ordinal then frame distance. For equal candidates, maintain a rotating player cursor. `tick()` resets map/byte counters, sends already-read eligible pixels, then starts at most the remaining read slots. Each read validates a 16,384-byte result; its completion returns through the injected global executor. Before every send, confirm the demand still has sources. A failed read reports the injected `Consumer<IOException>` and leaves the demand queued for retry on a future tick.

- [ ] **Step 4: Run the scheduler tests to verify success**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VirtualMapDeliverySchedulerTest`

Expected: PASS.

### Task 3: Make virtual still attachments demand-driven

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualStillMapService.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/map/VirtualStillMapServiceTest.java`

- [ ] **Step 1: Write failing attachment lifecycle tests**

```java
@Test void detachingTheLastSourceCancelsItsUnsentDemand() { /* attach, detach, tick, no load */ }
@Test void retainsOneDemandForTwoSourcesSharingPlayerAndMap() { /* attach twice, one send */ }
@Test void attachesTheHandPriorityBeforeAFramePriority() { /* scheduler observes main hand */ }
```

- [ ] **Step 2: Run the service tests to verify failure**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VirtualStillMapServiceTest`

Expected: FAIL because `attach` has no priority/distance parameter and still starts a read directly.

- [ ] **Step 3: Replace direct read/send state with scheduler delegation**

```java
public void attach(String source, Player player, MediaMapDescriptor descriptor,
        VirtualMapDeliveryScheduler.Priority priority, long distanceSquared, PixelLoader loader) {
    scheduler.attach(source, player, descriptor.mapId(), priority, distanceSquared, loader::load);
}

public void detach(String source) { scheduler.detach(source); }
public void clear() { scheduler.clear(); }
public void tick() { scheduler.tick(); }
```

Keep `PixelLoader` as the checked-`IOException` boundary. Remove the old attachment maps, immediate async load, pixel retention, and direct packet sender from this class.

- [ ] **Step 4: Run the focused service tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VirtualStillMapServiceTest`

Expected: PASS.

### Task 4: Feed source priority and lifecycle into the scheduler

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Modify: `folia/src/test/java/dev/tobyscamera/folia/map/MediaMapActivationListenerTest.java`

- [ ] **Step 1: Write failing source-priority tests**

```java
@Test void reconcilesMainHandAsHigherPriorityThanOffHand() { /* inspect injected still service */ }
@Test void reconcilesFramesWithTheirDistanceFromTheViewer() { /* frame chunk and viewer location */ }
@Test void chunkUnloadRemovesTheFrameDemandBeforeItCanSend() { /* queued demand is detached */ }
```

- [ ] **Step 2: Run activation tests to verify failure**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapActivationListenerTest`

Expected: FAIL because activation does not provide source priority or distance.

- [ ] **Step 3: Pass explicit source metadata at every reconciliation point**

```java
// Hands
reconcile(playerSource(player, "main"), player, mainHand, Priority.MAIN_HAND, 0L);
reconcile(playerSource(player, "off"), player, offHand, Priority.OFF_HAND, 0L);

// Frames
long distanceSquared = frame.getLocation().distanceSquared(player.getLocation());
reconcile(source, player, frame.getItem(), Priority.FRAME, distanceSquared);
```

Add a package-visible constructor that accepts a `VirtualStillMapService` for lifecycle tests. Production construction retains the scheduler-backed service. Each existing detach path remains unchanged and therefore cancels queued demand through the service.

- [ ] **Step 4: Run activation and scheduler tests**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.MediaMapActivationListenerTest --tests dev.tobyscamera.folia.map.VirtualMapDeliverySchedulerTest`

Expected: PASS.

### Task 5: Wire global ticks and reloadable limits

**Files:**
- Modify: `folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java`
- Modify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`
- Test: `folia/src/test/java/dev/tobyscamera/folia/config/PluginSettingsTest.java`

- [ ] **Step 1: Write a failing wiring test or focused constructor test**

```java
@Test void replacesDeliveryLimitsWithoutDiscardingActiveSources() {
    // Create listener with default limits, attach a source, update limits, tick, assert its demand remains deliverable.
}
```

- [ ] **Step 2: Run it to verify failure**

Run: `./gradlew.bat :folia:test --tests dev.tobyscamera.folia.map.VirtualStillMapServiceTest`

Expected: FAIL because limits cannot be updated.

- [ ] **Step 3: Schedule and reload delivery ticks**

```java
// MediaMapActivationListener
public void setDeliveryLimits(VirtualMapDeliveryScheduler.Limits limits) { stills.setLimits(limits); }
public void tickDelivery() { stills.tick(); }

// TobysCameraPlugin
deliveryTickTask = scheduler.runGlobalRepeating(1L, 1L, mediaActivation::tickDelivery);
mediaActivation.setDeliveryLimits(VirtualMapDeliveryScheduler.Limits.from(settings));
```

Create and cancel `deliveryTickTask` alongside the existing global task. On reload, replace limits in the live scheduler; on plugin disable, cancel the task before clearing media activation.

- [ ] **Step 4: Run the Folia module test suite**

Run: `./gradlew.bat :folia:test`

Expected: PASS.

### Task 6: Perform final verification

**Files:**
- Verify: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualMapDeliveryScheduler.java`
- Verify: `folia/src/main/java/dev/tobyscamera/folia/map/VirtualStillMapService.java`
- Verify: `folia/src/main/java/dev/tobyscamera/folia/map/MediaMapActivationListener.java`

- [ ] **Step 1: Run the complete relevant test suite**

Run: `./gradlew.bat :folia:test :common:test`

Expected: PASS with zero failed tests.

- [ ] **Step 2: Verify required integration points**

Run: `rg -n "runAsync|sendFull|virtual-map-delivery|maxConcurrentReads|detach\\(" folia/src/main/java/dev/tobyscamera/folia/map folia/src/main/java/dev/tobyscamera/folia/TobysCameraPlugin.java folia/src/main/resources/config.yml`

Expected: direct map sends occur only through `VirtualMapDeliveryScheduler`; `VirtualStillMapService` does not invoke `runAsync`; configuration and one-tick global scheduling are present; all source teardown paths still call `detach`.
