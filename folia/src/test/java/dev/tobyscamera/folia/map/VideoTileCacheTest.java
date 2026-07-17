package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.storage.TileCoordinate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VideoTileCacheTest {
    @Test
    void loadsTheSameVideoTileOnlyOnceUntilItIsEvicted() throws Exception {
        VideoTileCache cache = new VideoTileCache(1);
        UUID video = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        VideoTileCache.Key first = new VideoTileCache.Key(video, 0, new TileCoordinate(0, 0));

        cache.get(first, () -> new byte[] {(byte) loads.incrementAndGet()});
        cache.get(first, () -> new byte[] {(byte) loads.incrementAndGet()});
        cache.get(new VideoTileCache.Key(video, 1, new TileCoordinate(0, 0)), () -> new byte[] {3});
        cache.get(first, () -> new byte[] {(byte) loads.incrementAndGet()});

        assertEquals(2, loads.get());
    }
}
