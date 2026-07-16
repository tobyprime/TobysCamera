package dev.tobyscamera.folia.storage;
import java.time.Instant; import java.util.Map; import java.util.UUID;
public record VideoRecord(UUID videoId, UUID ownerId, Instant createdAt, int gridWidth, int gridHeight, int fps, int frameCount, Map<TileCoordinate,Integer> mapIds) { public VideoRecord { mapIds=Map.copyOf(mapIds); } }
