package dev.tobyscamera.folia.storage;

public record PhotoStorageStats(long photoCount, long tileCount) {
    public PhotoStorageStats {
        if (photoCount < 0 || tileCount < 0) {
            throw new IllegalArgumentException("storage totals must be non-negative");
        }
    }
}
