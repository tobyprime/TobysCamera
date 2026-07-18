package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.folia.storage.MediaTileCache;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.storage.VideoRecord;
import dev.tobyscamera.folia.storage.VideoRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MediaServiceCacheTest {
    @Test
    void cachesAStaticPhotoTileAcrossActivations() throws Exception {
        PhotoRepository repository = Mockito.mock(PhotoRepository.class);
        MediaTileCache cache = new MediaTileCache(2L * MediaTileCache.TILE_BYTES);
        UUID photoId = UUID.randomUUID();
        MediaMapDescriptor.PhotoTile tile = new MediaMapDescriptor.PhotoTile(7, photoId, new TileCoordinate(0, 0));
        byte[] pixels = pixels((byte) 31);
        when(repository.readTile(photoId, tile.coordinate())).thenReturn(pixels);
        MapPhotoService photos = new MapPhotoService(null, repository, cache);

        assertArrayEquals(pixels, photos.tilePixels(tile));
        assertArrayEquals(pixels, photos.tilePixels(tile));

        verify(repository, times(1)).readTile(photoId, tile.coordinate());
    }

    @Test
    void cachesTheVideoFirstFrameAcrossDuplicateActivations() throws Exception {
        VideoRepository repository = Mockito.mock(VideoRepository.class);
        MediaTileCache cache = new MediaTileCache(2L * MediaTileCache.TILE_BYTES);
        UUID videoId = UUID.randomUUID();
        TileCoordinate coordinate = new TileCoordinate(0, 0);
        VideoRecord record = new VideoRecord(videoId, UUID.randomUUID(), Instant.now(), 1, 1, 10, 2, Map.of(coordinate, 8));
        MediaMapDescriptor.VideoTile tile = new MediaMapDescriptor.VideoTile(8, videoId, coordinate);
        byte[] pixels = pixels((byte) 73);
        when(repository.find(videoId)).thenReturn(record);
        when(repository.readTile(videoId, 0, coordinate)).thenReturn(pixels);
        MapVideoService videos = new MapVideoService(repository, cache);

        assertArrayEquals(pixels, videos.load(tile).firstFrame());
        assertArrayEquals(pixels, videos.load(tile).firstFrame());

        verify(repository, times(1)).find(videoId);
        verify(repository, times(1)).readTile(videoId, 0, coordinate);
    }

    private static byte[] pixels(byte value) {
        byte[] pixels = new byte[MediaTileCache.TILE_BYTES];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
