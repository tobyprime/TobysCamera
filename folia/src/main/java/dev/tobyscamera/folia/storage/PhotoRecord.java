package dev.tobyscamera.folia.storage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PhotoRecord(UUID photoId, UUID ownerId, Instant createdAt, int gridWidth, int gridHeight,
        Map<TileCoordinate, Integer> mapIds) {
    public PhotoRecord {
        if (gridWidth < 1 || gridHeight < 1) {
            throw new IllegalArgumentException("grid must be positive");
        }
        mapIds = Map.copyOf(mapIds);
        if (mapIds.size() != gridWidth * gridHeight) throw new IllegalArgumentException("map ids must cover every tile");
    }
}
