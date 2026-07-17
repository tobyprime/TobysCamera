package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.tobyscamera.folia.storage.TileCoordinate;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VideoTileCacheTest {
    @Test
    void reportsACacheMissWithoutLoadingTheTile() {
        VideoTileCache cache = new VideoTileCache(1);

        assertNull(cache.find(new VideoTileCache.Key(UUID.randomUUID(), 0, new TileCoordinate(0, 0))));
    }

    @Test
    void readsCacheMissesWhileAnotherThreadLoadsFromStorage() throws Exception {
        VideoTileCache cache = new VideoTileCache(2);
        UUID video = UUID.randomUUID();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread loader = new Thread(() -> {
            try {
                cache.get(new VideoTileCache.Key(video, 0, new TileCoordinate(0, 0)), () -> {
                    loading.countDown();
                    try { release.await(); }
                    catch (InterruptedException exception) { throw new java.io.IOException(exception); }
                    return new byte[] {1};
                });
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        loader.start();
        loading.await();
        try {
            assertTimeoutPreemptively(Duration.ofMillis(100),
                    () -> assertNull(cache.find(new VideoTileCache.Key(video, 1, new TileCoordinate(0, 0)))));
        } finally {
            release.countDown();
            loader.join();
        }
    }

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
