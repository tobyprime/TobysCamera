package dev.tobyscamera.folia.storage;

import dev.tobyscamera.common.upload.VideoUploadSession;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface VideoRepository extends AutoCloseable {
    void save(VideoRecord record, VideoUploadSession session) throws IOException;
    List<VideoRecord> loadAll() throws IOException;
    VideoRecord find(UUID videoId) throws IOException;
    byte[] readTile(UUID videoId, int frameIndex, TileCoordinate coordinate) throws IOException;
    byte[] readPreview(UUID videoId) throws IOException;
    @Override void close() throws IOException;
}
