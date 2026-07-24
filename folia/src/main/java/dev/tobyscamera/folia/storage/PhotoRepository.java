package dev.tobyscamera.folia.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PhotoRepository extends AutoCloseable {
    void save(PhotoRecord record, Map<TileCoordinate, byte[]> tiles, byte[] previewPixels) throws IOException;
    List<PhotoRecord> loadAll() throws IOException;
    PhotoPage findPage(PhotoQuery query) throws IOException;
    PhotoOwnerPage findOwners(PhotoQuery query) throws IOException;
    PhotoPage findPageForOwner(UUID ownerId, PhotoQuery query) throws IOException;
    PhotoStorageStats stats() throws IOException;
    PhotoRecord find(UUID photoId) throws IOException;
    byte[] readTile(UUID photoId, TileCoordinate coordinate) throws IOException;
    byte[] readPreview(UUID photoId) throws IOException;
    boolean isBlocked(UUID playerId) throws IOException;
    void block(UploadBlock block) throws IOException;
    void unblock(UUID playerId) throws IOException;
    void delete(UUID photoId) throws IOException;
    @Override void close() throws IOException;
}
