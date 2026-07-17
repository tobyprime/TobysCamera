package dev.tobyscamera.folia.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoPlaybackIndexTest {
    @Test
    void selectsFrameAcrossChunkBoundaryAndOnlyItsNearbyViewer() {
        VideoPlaybackIndex index = new VideoPlaybackIndex();
        UUID world = UUID.randomUUID(), near = UUID.randomUUID(), far = UUID.randomUUID();
        index.upsertFrame(UUID.randomUUID(), 42, world, 16, 64, 0);
        index.upsertViewer(near, world, 15, 64, 0, Set.of());
        index.upsertViewer(far, world, 400, 64, 0, Set.of());

        assertEquals(Map.of(42, Set.of(near)), index.activeViewers(8, 32));
    }

    @Test
    void keepsHeldVideoActiveWithoutANearbyFrame() {
        VideoPlaybackIndex index = new VideoPlaybackIndex();
        UUID world = UUID.randomUUID(), holder = UUID.randomUUID();
        index.upsertViewer(holder, world, 0, 64, 0, Set.of(99));

        assertEquals(Map.of(99, Set.of(holder)), index.activeViewers(8, 32));
    }

    @Test
    void deduplicatesFramesAndUsesNearestMapsWithinTheActiveBudget() {
        VideoPlaybackIndex index = new VideoPlaybackIndex();
        UUID world = UUID.randomUUID(), viewer = UUID.randomUUID();
        index.upsertViewer(viewer, world, 0, 64, 0, Set.of());
        index.upsertFrame(UUID.randomUUID(), 7, world, 5, 64, 0);
        index.upsertFrame(UUID.randomUUID(), 7, world, 6, 64, 0);
        index.upsertFrame(UUID.randomUUID(), 8, world, 10, 64, 0);

        assertEquals(Map.of(7, Set.of(viewer)), index.activeViewers(1, 32));
    }

    @Test
    void neverCrossesWorlds() {
        VideoPlaybackIndex index = new VideoPlaybackIndex();
        UUID overworld = UUID.randomUUID(), nether = UUID.randomUUID(), viewer = UUID.randomUUID();
        index.upsertViewer(viewer, overworld, 0, 64, 0, Set.of());
        index.upsertFrame(UUID.randomUUID(), 5, nether, 0, 64, 0);

        assertEquals(Map.of(), index.activeViewers(8, 32));
    }
}
