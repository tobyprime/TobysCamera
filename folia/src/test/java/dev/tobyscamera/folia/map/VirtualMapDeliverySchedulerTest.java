package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class VirtualMapDeliverySchedulerTest {
    @Test
    void startsHandDemandBeforeFrameWhenOneReadSlotIsAvailable() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(1, 4, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("frame", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> fixture.namedTile("frame"));
        fixture.scheduler.attach("main", player, 2, VirtualMapDeliveryScheduler.Priority.MAIN_HAND, 0L, () -> fixture.namedTile("main"));

        fixture.scheduler.tick();
        fixture.runAsync();

        assertEquals(List.of("main"), fixture.loaded);
    }

    @Test
    void deduplicatesSourcesForTheSamePlayerAndMap() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(2, 4, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 11, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> fixture.namedTile("one"));
        fixture.scheduler.attach("two", player, 11, VirtualMapDeliveryScheduler.Priority.MAIN_HAND, 0L, () -> fixture.namedTile("two"));

        fixture.scheduler.tick();
        fixture.runAsync();
        fixture.runGlobal();
        fixture.scheduler.tick();

        assertEquals(List.of("two"), fixture.loaded);
        verify(fixture.sender, times(1)).sendFull(eq(player), eq(11), any(byte[].class));
    }

    @Test
    void limitsConcurrentReads() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(2, 4, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("two", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("three", player, 3, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);

        fixture.scheduler.tick();

        assertEquals(2, fixture.async.size());
    }

    @Test
    void doesNotPreReadMoreTilesThanTheNextGlobalDeliveryBudgetCanSend() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(12, 4, 65_536L, 16_384L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("two", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("three", player, 3, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);

        fixture.scheduler.tick();

        assertEquals(1, fixture.async.size());
    }

    @Test
    void keepsAnInFlightReadReservedAgainstTheNextGlobalDeliveryBudget() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(12, 4, 65_536L, 16_384L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("two", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.tick();
        fixture.scheduler.tick();

        assertEquals(1, fixture.async.size());
    }

    @Test
    void appliesThePerPlayerMapBudgetBeforeStartingReads() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(12, 1, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("two", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);

        fixture.scheduler.tick();

        assertEquals(1, fixture.async.size());
    }

    @Test
    void startsTheNearestFrameBeforeAfartherFrame() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(1, 4, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("far", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 25L, () -> fixture.namedTile("far"));
        fixture.scheduler.attach("near", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 1L, () -> fixture.namedTile("near"));

        fixture.scheduler.tick();
        fixture.runAsync();

        assertEquals(List.of("near"), fixture.loaded);
    }

    @Test
    void appliesTheGlobalByteBudgetPerTick() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(2, 4, 65_536L, 16_384L));
        Player player = fixture.player();
        fixture.scheduler.attach("one", player, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);
        fixture.scheduler.attach("two", player, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, fixture::tile);

        fixture.scheduler.tick();
        fixture.runAsync();
        fixture.runGlobal();
        fixture.scheduler.tick();

        verify(fixture.sender, times(1)).sendFull(eq(player), any(Integer.class), any(byte[].class));
    }

    @Test
    void rotatesEqualPriorityPlayersBeforeSchedulingTheirSecondDemand() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(3, 4, 65_536L, 65_536L));
        Player first = fixture.player();
        Player second = fixture.player();
        fixture.scheduler.attach("first-a", first, 1, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> fixture.namedTile("first-a"));
        fixture.scheduler.attach("first-b", first, 2, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> fixture.namedTile("first-b"));
        fixture.scheduler.attach("second", second, 3, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> fixture.namedTile("second"));

        fixture.scheduler.tick();
        fixture.runAsync();

        assertEquals(List.of("first-a", "second", "first-b"), fixture.loaded);
    }

    @Test
    void discardsReadCompletionAfterLastSourceIsDetached() {
        Fixture fixture = new Fixture(new VirtualMapDeliveryScheduler.Limits(1, 4, 65_536L, 65_536L));
        Player player = fixture.player();
        fixture.scheduler.attach("source", player, 9, VirtualMapDeliveryScheduler.Priority.MAIN_HAND, 0L, fixture::tile);
        fixture.scheduler.tick();
        fixture.scheduler.detach("source");
        fixture.runAsync();
        fixture.runGlobal();
        fixture.scheduler.tick();

        verify(fixture.sender, never()).sendFull(any(), any(Integer.class), any(byte[].class));
    }

    private static final class Fixture {
        final List<Runnable> async = new ArrayList<>();
        final List<Runnable> global = new ArrayList<>();
        final List<String> loaded = new ArrayList<>();
        final VirtualMapPacketSender sender = mock(VirtualMapPacketSender.class);
        final VirtualMapDeliveryScheduler scheduler;

        Fixture(VirtualMapDeliveryScheduler.Limits limits) {
            scheduler = new VirtualMapDeliveryScheduler(async::add, global::add, ignored -> { }, sender, limits);
        }

        Player player() {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            return player;
        }

        byte[] tile() { return new byte[16_384]; }
        byte[] namedTile(String name) { loaded.add(name); return tile(); }
        void runAsync() { run(async); }
        void runGlobal() { run(global); }
        private static void run(List<Runnable> tasks) { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }
}
