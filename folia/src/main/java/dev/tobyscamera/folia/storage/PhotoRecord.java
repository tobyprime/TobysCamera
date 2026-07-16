package dev.tobyscamera.folia.storage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PhotoRecord(UUID photoId, UUID ownerId, Instant createdAt, PhotoCoordinates coordinates, int gridWidth, int gridHeight,
        Map<TileCoordinate, Integer> mapIds) {
    public PhotoRecord {
        if (coordinates == null) throw new IllegalArgumentException("coordinates are required");
        if (gridWidth < 1 || gridWidth > 4 || gridHeight < 1 || gridHeight > 4) {
            throw new IllegalArgumentException("grid must be 1..4");
        }
        mapIds = Map.copyOf(mapIds);
        if (mapIds.size() != gridWidth * gridHeight) throw new IllegalArgumentException("map ids must cover every tile");
    }
}
