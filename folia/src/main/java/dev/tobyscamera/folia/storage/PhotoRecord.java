package dev.tobyscamera.folia.storage;

import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PhotoRecord(UUID photoId, UUID ownerId, String ownerName, Instant createdAt, int gridWidth, int gridHeight,
        Map<TileCoordinate, Integer> mapIds, PhotoMetadata metadata) {
    public PhotoRecord(UUID photoId, UUID ownerId, Instant createdAt, int gridWidth, int gridHeight,
            Map<TileCoordinate, Integer> mapIds) {
        this(photoId, ownerId, null, createdAt, gridWidth, gridHeight, mapIds, null);
    }

    public PhotoRecord {
        if (gridWidth < 1 || gridHeight < 1) {
            throw new IllegalArgumentException("grid must be positive");
        }
        mapIds = Map.copyOf(mapIds);
        if (mapIds.size() != gridWidth * gridHeight) throw new IllegalArgumentException("map ids must cover every tile");
    }
}
