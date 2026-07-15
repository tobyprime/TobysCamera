package dev.tobyscamera.folia.storage;

public record TileCoordinate(int x, int y) {
    public TileCoordinate {
        if (x < 0 || y < 0) throw new IllegalArgumentException("tile coordinates cannot be negative");
    }
}
