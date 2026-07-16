package dev.tobyscamera.folia.storage;

/** Immutable block position captured when the camera shutter was pressed. */
public record PhotoCoordinates(String world, int x, int y, int z) {
    public PhotoCoordinates {
        if (world == null || world.isBlank()) throw new IllegalArgumentException("world is required");
    }

    public String display() { return world + " " + x + ", " + y + ", " + z; }
}
