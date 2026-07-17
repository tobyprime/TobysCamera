package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.storage.TileCoordinate;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded access-ordered cache of decoded video map tiles. */
final class VideoTileCache {
    private final Map<Key, byte[]> entries;

    VideoTileCache(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        entries = new LinkedHashMap<>(maximumEntries, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Key, byte[]> eldest) { return size() > maximumEntries; }
        };
    }

    byte[] get(Key key, Loader loader) throws IOException {
        synchronized (this) {
            byte[] cached = entries.get(key);
            if (cached != null) return cached;
        }
        byte[] loaded = loader.load();
        synchronized (this) {
            byte[] cached = entries.get(key);
            if (cached != null) return cached;
            entries.put(key, loaded);
            return loaded;
        }
    }

    /** Returns an already-loaded tile without invoking storage. */
    synchronized byte[] find(Key key) {
        return entries.get(key);
    }

    record Key(UUID videoId, int frameIndex, TileCoordinate coordinate) { }
    @FunctionalInterface interface Loader { byte[] load() throws IOException; }
}
