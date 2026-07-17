package dev.tobyscamera.folia.video;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Deduplicates asynchronous cache-fill work for one video map frame. */
final class VideoFrameLoadRequests {
    private final Set<Key> pending = new HashSet<>();

    synchronized boolean begin(Key key) {
        return pending.add(key);
    }

    synchronized void complete(Key key) {
        pending.remove(key);
    }

    record Key(UUID videoId, int mapId, int frameIndex) { }
}
