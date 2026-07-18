package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkFrameViewerTrackerTest {
    @Test
    void replacesAViewerChunkAndReleasesOnlyFramesNoLongerVisibleThere() {
        ChunkFrameViewerTracker tracker = new ChunkFrameViewerTracker();
        var viewerChunk = new ChunkFrameViewerTracker.ViewerChunk(UUID.randomUUID(), UUID.randomUUID(), 4, -2);
        UUID retained = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        UUID added = UUID.randomUUID();

        assertEquals(Set.of(), tracker.replace(viewerChunk, Set.of(retained, removed)));
        assertEquals(Set.of(removed), tracker.replace(viewerChunk, Set.of(retained, added)));
        assertEquals(Set.of(viewerChunk), tracker.viewers(retained));
        assertEquals(Set.of(), tracker.viewers(removed));
        assertEquals(Set.of(viewerChunk), tracker.viewers(added));
    }

    @Test
    void releasesAllFrameSourcesWhenTheClientUnloadsAChunk() {
        ChunkFrameViewerTracker tracker = new ChunkFrameViewerTracker();
        var viewerChunk = new ChunkFrameViewerTracker.ViewerChunk(UUID.randomUUID(), UUID.randomUUID(), 0, 0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.replace(viewerChunk, Set.of(first, second));

        assertEquals(Set.of(first, second), tracker.release(viewerChunk));
        assertTrue(tracker.viewers(first).isEmpty());
        assertTrue(tracker.viewers(second).isEmpty());
    }

    @Test
    void releasesEveryViewerChunkWhenThePlayerLeaves() {
        ChunkFrameViewerTracker tracker = new ChunkFrameViewerTracker();
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        var firstChunk = new ChunkFrameViewerTracker.ViewerChunk(player, world, 0, 0);
        var secondChunk = new ChunkFrameViewerTracker.ViewerChunk(player, world, 1, 0);
        tracker.replace(firstChunk, Set.of(UUID.randomUUID()));
        tracker.replace(secondChunk, Set.of(UUID.randomUUID()));

        assertEquals(Set.of(firstChunk, secondChunk), tracker.releasePlayer(player).keySet());
    }
}
