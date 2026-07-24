package dev.tobyscamera.folia.storage;

import java.time.Instant;
import java.util.UUID;

/** One uploader represented in the gallery's player index. */
public record PhotoOwner(UUID ownerId, String ownerName, long photoCount, Instant latestCreatedAt) {
    public PhotoOwner {
        if (photoCount < 1) throw new IllegalArgumentException("photo count must be positive");
    }
}
