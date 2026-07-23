package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MediaTileCacheTest {
    @Test
    void invalidationPreventsAnInFlightPhotoLoaderFromRestoringStalePixels() throws Exception {
        MediaTileCache cache = new MediaTileCache(16_384);
        UUID photoId = UUID.randomUUID();
        MediaTileCache.Key key = MediaTileCache.Key.photoPreview(photoId);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        byte[] pixels = new byte[16_384];
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> cache.getOrLoad(key, () -> {
                loading.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("test loader timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("test loader was interrupted", exception);
                }
                return pixels;
            }));

            assertTrue(loading.await(5, TimeUnit.SECONDS));
            cache.invalidatePhoto(photoId);
            release.countDown();

            assertArrayEquals(pixels, result.get(5, TimeUnit.SECONDS));
            assertNull(cache.find(key));
        } finally {
            executor.shutdownNow();
        }
    }
}
