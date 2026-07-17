package dev.tobyscamera.folia.bag;

/** Integer vector in block/item-frame grid coordinates. */
public record GridVector(int x, int y, int z) {
    public GridVector multiply(int scalar) { return new GridVector(x * scalar, y * scalar, z * scalar); }
    public GridVector add(GridVector other) { return new GridVector(x + other.x, y + other.y, z + other.z); }
}
