package dev.tobyscamera.folia.status;

import dev.tobyscamera.folia.storage.PhotoStorageStats;

public final class PluginRuntimeStatus {
    private long runPhotos;
    private long runTiles;
    private long storedPhotos;
    private long storedTiles;

    public PluginRuntimeStatus(PhotoStorageStats stored) { storedPhotos = stored.photoCount(); storedTiles = stored.tileCount(); }
    public synchronized void recordPersisted(int tiles) { runPhotos++; runTiles += tiles; storedPhotos++; storedTiles += tiles; }
    public synchronized Totals totals() { return new Totals(runPhotos, runTiles, storedPhotos, storedTiles); }
    public record Totals(long runPhotos, long runTiles, long storedPhotos, long storedTiles) { }
}
