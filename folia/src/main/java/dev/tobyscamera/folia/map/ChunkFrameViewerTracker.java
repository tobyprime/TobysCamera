package dev.tobyscamera.folia.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks only the frame sources currently visible through player-delivered chunks. */
final class ChunkFrameViewerTracker {
    private final Map<ViewerChunk, Set<UUID>> framesByViewerChunk = new HashMap<>();
    private final Map<UUID, Set<ViewerChunk>> viewersByFrame = new HashMap<>();

    synchronized Set<UUID> replace(ViewerChunk viewerChunk, Set<UUID> frameIds) {
        Set<UUID> next = Set.copyOf(frameIds);
        Set<UUID> previous = framesByViewerChunk.put(viewerChunk, next);
        if (previous == null) previous = Set.of();
        for (UUID frameId : previous) if (!next.contains(frameId)) removeViewer(frameId, viewerChunk);
        for (UUID frameId : next) if (!previous.contains(frameId)) {
            viewersByFrame.computeIfAbsent(frameId, ignored -> new HashSet<>()).add(viewerChunk);
        }
        Set<UUID> removed = new HashSet<>(previous);
        removed.removeAll(next);
        return Set.copyOf(removed);
    }

    synchronized Set<UUID> release(ViewerChunk viewerChunk) {
        Set<UUID> frames = framesByViewerChunk.remove(viewerChunk);
        if (frames == null) return Set.of();
        for (UUID frameId : frames) removeViewer(frameId, viewerChunk);
        return frames;
    }

    synchronized Set<ViewerChunk> viewers(UUID frameId) {
        Set<ViewerChunk> viewers = viewersByFrame.get(frameId);
        return viewers == null ? Set.of() : Set.copyOf(viewers);
    }

    /** Returns players whose client has already received this chunk, even before it contains a frame. */
    synchronized Set<ViewerChunk> viewersIn(UUID worldId, int chunkX, int chunkZ) {
        Set<ViewerChunk> viewers = new HashSet<>();
        for (ViewerChunk viewer : framesByViewerChunk.keySet()) {
            if (viewer.worldId().equals(worldId) && viewer.chunkX() == chunkX && viewer.chunkZ() == chunkZ) viewers.add(viewer);
        }
        return Set.copyOf(viewers);
    }

    /** Adds a frame that appeared after this viewer's chunk was delivered. */
    synchronized void trackFrame(ViewerChunk viewerChunk, UUID frameId) {
        Set<UUID> frames = framesByViewerChunk.get(viewerChunk);
        if (frames == null || frames.contains(frameId)) return;
        Set<UUID> updated = new HashSet<>(frames);
        updated.add(frameId);
        framesByViewerChunk.put(viewerChunk, Set.copyOf(updated));
        viewersByFrame.computeIfAbsent(frameId, ignored -> new HashSet<>()).add(viewerChunk);
    }

    synchronized Set<ViewerChunk> removeFrame(UUID frameId) {
        Set<ViewerChunk> viewers = viewersByFrame.remove(frameId);
        if (viewers == null) return Set.of();
        for (ViewerChunk viewer : viewers) {
            Set<UUID> frames = framesByViewerChunk.get(viewer);
            if (frames == null) continue;
            Set<UUID> remaining = new HashSet<>(frames);
            remaining.remove(frameId);
            framesByViewerChunk.put(viewer, Set.copyOf(remaining));
        }
        return Set.copyOf(viewers);
    }

    synchronized Map<ViewerChunk, Set<UUID>> releasePlayer(UUID playerId) {
        Map<ViewerChunk, Set<UUID>> released = new HashMap<>();
        for (ViewerChunk viewer : Set.copyOf(framesByViewerChunk.keySet())) {
            if (!viewer.playerId().equals(playerId)) continue;
            released.put(viewer, release(viewer));
        }
        return Map.copyOf(released);
    }

    private void removeViewer(UUID frameId, ViewerChunk viewerChunk) {
        Set<ViewerChunk> viewers = viewersByFrame.get(frameId);
        if (viewers == null) return;
        viewers.remove(viewerChunk);
        if (viewers.isEmpty()) viewersByFrame.remove(frameId);
    }

    record ViewerChunk(UUID playerId, UUID worldId, int chunkX, int chunkZ) { }
}
