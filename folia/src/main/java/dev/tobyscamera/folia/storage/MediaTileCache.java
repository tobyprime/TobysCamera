package dev.tobyscamera.folia.storage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/** Shared, byte-bounded cache for immutable 128×128 palette tiles and previews. */
public final class MediaTileCache {
    public static final int TILE_BYTES = 16_384;
    private final long maximumBytes;
    private final Map<Key, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Key, CompletableFuture<byte[]>> loading = new ConcurrentHashMap<>();
    private long cachedBytes;

    public MediaTileCache(long maximumBytes) {
        if (maximumBytes < TILE_BYTES) throw new IllegalArgumentException("media cache must fit one tile");
        this.maximumBytes = maximumBytes;
    }

    public byte[] getOrLoad(Key key, Loader loader) throws IOException {
        synchronized (this) {
            byte[] cached = entries.get(key);
            if (cached != null) return cached;
        }
        CompletableFuture<byte[]> created = new CompletableFuture<>();
        CompletableFuture<byte[]> pending = loading.putIfAbsent(key, created);
        if (pending == null) {
            try {
                byte[] pixels = loader.load();
                if (pixels == null || pixels.length != TILE_BYTES) throw new IOException("cached media tile must contain 16384 bytes");
                synchronized (this) { put(key, pixels); }
                created.complete(pixels);
                return pixels;
            } catch (IOException exception) {
                created.completeExceptionally(exception);
                throw exception;
            } catch (RuntimeException exception) {
                created.completeExceptionally(exception);
                throw exception;
            } finally {
                loading.remove(key, created);
            }
        }
        try {
            return pending.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading cached media", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IOException("could not read cached media", cause);
        }
    }

    public synchronized byte[] find(Key key) { return entries.get(key); }

    /** Removes every cached preview and tile belonging to a deleted photo. */
    public synchronized void invalidatePhoto(UUID photoId) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, byte[]> entry = iterator.next();
            if (!entry.getKey().mediaId().equals(photoId)) continue;
            cachedBytes -= entry.getValue().length;
            iterator.remove();
        }
    }

    private void put(Key key, byte[] pixels) {
        byte[] prior = entries.put(key, pixels);
        if (prior != null) cachedBytes -= prior.length;
        cachedBytes += pixels.length;
        var iterator = entries.entrySet().iterator();
        while (cachedBytes > maximumBytes && iterator.hasNext()) {
            byte[] evicted = iterator.next().getValue();
            iterator.remove();
            cachedBytes -= evicted.length;
        }
    }

    @FunctionalInterface
    public interface Loader { byte[] load() throws IOException; }

    public record Key(Kind kind, UUID mediaId, int frameIndex, TileCoordinate coordinate) {
        public static Key photoTile(UUID photoId, TileCoordinate coordinate) { return new Key(Kind.PHOTO_TILE, photoId, 0, coordinate); }
        public static Key photoPreview(UUID photoId) { return new Key(Kind.PHOTO_PREVIEW, photoId, 0, null); }
    }

    public enum Kind { PHOTO_TILE, PHOTO_PREVIEW }
}
