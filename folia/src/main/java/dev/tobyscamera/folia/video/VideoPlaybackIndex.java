package dev.tobyscamera.folia.video;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spatial index for video maps and their viewers; all operations use immutable value snapshots. */
public final class VideoPlaybackIndex {
    private static final int CHUNK_SIZE = 16;
    private final Map<UUID, Frame> frames = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final Map<UUID, Map<ChunkPosition, Map<UUID, Frame>>> framesByChunk = new HashMap<>();
    private final Map<UUID, Map<ChunkPosition, Set<UUID>>> viewersByChunk = new HashMap<>();
    private Map<Integer, Set<UUID>> visibleSnapshot = Map.of();
    private int snapshotActiveLimit = -1;
    private int snapshotMaximumDistance = -1;
    private boolean visibleSnapshotDirty = true;

    public synchronized void upsertFrame(UUID frameId, int mapId, UUID worldId, double x, double y, double z) {
        invalidateVisibleSnapshot();
        removeFrame(frameId);
        Frame frame = new Frame(frameId, mapId, worldId, x, y, z);
        frames.put(frameId, frame);
        framesByChunk.computeIfAbsent(worldId, ignored -> new HashMap<>())
                .computeIfAbsent(chunk(x, z), ignored -> new HashMap<>()).put(frameId, frame);
    }

    public synchronized void removeFrame(UUID frameId) {
        invalidateVisibleSnapshot();
        Frame existing = frames.remove(frameId);
        if (existing == null) return;
        Map<ChunkPosition, Map<UUID, Frame>> world = framesByChunk.get(existing.worldId());
        if (world == null) return;
        ChunkPosition position = chunk(existing.x(), existing.z());
        Map<UUID, Frame> bucket = world.get(position);
        if (bucket != null) {
            bucket.remove(frameId);
            if (bucket.isEmpty()) world.remove(position);
        }
        if (world.isEmpty()) framesByChunk.remove(existing.worldId());
    }

    public synchronized void upsertViewer(UUID viewerId, UUID worldId, double x, double y, double z, Set<Integer> heldMapIds) {
        Viewer existing = viewers.get(viewerId);
        if (existing != null && existing.worldId().equals(worldId) && existing.heldMapIds().equals(heldMapIds)
                && distanceSquared(existing.x(), existing.y(), existing.z(), x, y, z) < 4.0) return;
        invalidateVisibleSnapshot();
        removeViewer(viewerId);
        Viewer viewer = new Viewer(viewerId, worldId, x, y, z, Set.copyOf(heldMapIds));
        viewers.put(viewerId, viewer);
        viewersByChunk.computeIfAbsent(worldId, ignored -> new HashMap<>())
                .computeIfAbsent(chunk(x, z), ignored -> new LinkedHashSet<>()).add(viewerId);
    }

    public synchronized void removeViewer(UUID viewerId) {
        invalidateVisibleSnapshot();
        Viewer existing = viewers.remove(viewerId);
        if (existing == null) return;
        Map<ChunkPosition, Set<UUID>> world = viewersByChunk.get(existing.worldId());
        if (world == null) return;
        ChunkPosition position = chunk(existing.x(), existing.z());
        Set<UUID> bucket = world.get(position);
        if (bucket != null) {
            bucket.remove(viewerId);
            if (bucket.isEmpty()) world.remove(position);
        }
        if (world.isEmpty()) viewersByChunk.remove(existing.worldId());
    }

    /** Returns each selected map and exactly the indexed viewers that should receive it. */
    public synchronized Map<Integer, Set<UUID>> activeViewers(int activeLimit, int maximumDistance) {
        if (!visibleSnapshotDirty && snapshotActiveLimit == activeLimit && snapshotMaximumDistance == maximumDistance) return visibleSnapshot;
        if (activeLimit < 1 || maximumDistance < 0 || viewers.isEmpty()) return cacheVisibleSnapshot(activeLimit, maximumDistance, Map.of());
        double maximumDistanceSquared = (double) maximumDistance * maximumDistance;
        int chunkRadius = (maximumDistance + CHUNK_SIZE - 1) / CHUNK_SIZE;
        Map<Integer, Candidate> nearest = new HashMap<>();
        Map<UUID, Set<Integer>> visibleMaps = new HashMap<>();
        for (Viewer viewer : viewers.values()) {
            Set<Integer> visible = new HashSet<>(viewer.heldMapIds());
            for (int mapId : viewer.heldMapIds()) nearest.merge(mapId, new Candidate(mapId, 0.0), VideoPlaybackIndex::nearest);
            Map<ChunkPosition, Map<UUID, Frame>> worldFrames = framesByChunk.get(viewer.worldId());
            if (worldFrames != null) for (int chunkX = chunk(viewer.x(), viewer.z()).x() - chunkRadius; chunkX <= chunk(viewer.x(), viewer.z()).x() + chunkRadius; chunkX++) {
                for (int chunkZ = chunk(viewer.x(), viewer.z()).z() - chunkRadius; chunkZ <= chunk(viewer.x(), viewer.z()).z() + chunkRadius; chunkZ++) {
                    Map<UUID, Frame> bucket = worldFrames.get(new ChunkPosition(chunkX, chunkZ));
                    if (bucket == null) continue;
                    for (Frame frame : bucket.values()) {
                        double distanceSquared = distanceSquared(viewer.x(), viewer.y(), viewer.z(), frame.x(), frame.y(), frame.z());
                        if (distanceSquared <= maximumDistanceSquared) {
                            visible.add(frame.mapId());
                            nearest.merge(frame.mapId(), new Candidate(frame.mapId(), distanceSquared), VideoPlaybackIndex::nearest);
                        }
                    }
                }
            }
            visibleMaps.put(viewer.viewerId(), Set.copyOf(visible));
        }
        Set<Integer> active = nearest.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceSquared).thenComparingInt(Candidate::mapId))
                .limit(activeLimit).map(Candidate::mapId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, Set<UUID>> result = new LinkedHashMap<>();
        for (int mapId : active) result.put(mapId, new LinkedHashSet<>());
        for (var entry : visibleMaps.entrySet()) for (int mapId : entry.getValue()) {
            Set<UUID> selectedViewers = result.get(mapId);
            if (selectedViewers != null) selectedViewers.add(entry.getKey());
        }
        Map<Integer, Set<UUID>> immutable = new LinkedHashMap<>();
        for (var entry : result.entrySet()) immutable.put(entry.getKey(), Set.copyOf(entry.getValue()));
        return cacheVisibleSnapshot(activeLimit, maximumDistance, Map.copyOf(immutable));
    }

    private void invalidateVisibleSnapshot() { visibleSnapshotDirty = true; }
    private Map<Integer, Set<UUID>> cacheVisibleSnapshot(int activeLimit, int maximumDistance, Map<Integer, Set<UUID>> snapshot) {
        snapshotActiveLimit = activeLimit;
        snapshotMaximumDistance = maximumDistance;
        visibleSnapshot = snapshot;
        visibleSnapshotDirty = false;
        return snapshot;
    }

    private static Candidate nearest(Candidate left, Candidate right) { return left.distanceSquared() <= right.distanceSquared() ? left : right; }
    private static ChunkPosition chunk(double x, double z) { return new ChunkPosition(Math.floorDiv((int) Math.floor(x), CHUNK_SIZE), Math.floorDiv((int) Math.floor(z), CHUNK_SIZE)); }
    private static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) { double x = x1 - x2, y = y1 - y2, z = z1 - z2; return x * x + y * y + z * z; }

    private record Frame(UUID frameId, int mapId, UUID worldId, double x, double y, double z) { }
    private record Viewer(UUID viewerId, UUID worldId, double x, double y, double z, Set<Integer> heldMapIds) { }
    private record Candidate(int mapId, double distanceSquared) { }
    private record ChunkPosition(int x, int z) { }
}
