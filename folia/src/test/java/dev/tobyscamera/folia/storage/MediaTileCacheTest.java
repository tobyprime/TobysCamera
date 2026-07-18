package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MediaTileCacheTest {
    @Test
    void returnsTheCachedPixelsWithoutReloadingTheSameStaticTile() throws Exception {
        MediaTileCache cache = new MediaTileCache(2 * MediaTileCache.TILE_BYTES);
        MediaTileCache.Key key = MediaTileCache.Key.photoTile(UUID.randomUUID(), new TileCoordinate(0, 0));
        AtomicInteger loads = new AtomicInteger();

        byte[] first = cache.getOrLoad(key, () -> pixels((byte) loads.incrementAndGet()));
        byte[] second = cache.getOrLoad(key, () -> pixels((byte) loads.incrementAndGet()));

        assertEquals(1, loads.get());
        assertArrayEquals(first, second);
    }

    @Test
    void evictsTheLeastRecentlyUsedTileWhenItsByteBudgetIsFull() throws Exception {
        MediaTileCache cache = new MediaTileCache(2 * MediaTileCache.TILE_BYTES);
        MediaTileCache.Key first = MediaTileCache.Key.photoTile(UUID.randomUUID(), new TileCoordinate(0, 0));
        MediaTileCache.Key second = MediaTileCache.Key.videoTile(UUID.randomUUID(), 0, new TileCoordinate(0, 0));
        MediaTileCache.Key third = MediaTileCache.Key.photoPreview(UUID.randomUUID());
        AtomicInteger loads = new AtomicInteger();

        cache.getOrLoad(first, () -> pixels((byte) loads.incrementAndGet()));
        cache.getOrLoad(second, () -> pixels((byte) loads.incrementAndGet()));
        cache.getOrLoad(third, () -> pixels((byte) loads.incrementAndGet()));
        cache.getOrLoad(first, () -> pixels((byte) loads.incrementAndGet()));

        assertEquals(4, loads.get());
    }

    private static byte[] pixels(byte value) {
        byte[] pixels = new byte[MediaTileCache.TILE_BYTES];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
