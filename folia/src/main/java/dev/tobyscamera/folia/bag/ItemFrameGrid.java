package dev.tobyscamera.folia.bag;

import org.bukkit.block.BlockFace;

/** Face-local map coordinates: origin is visible upper-left, x grows right and y grows down. */
public record ItemFrameGrid(GridVector right, GridVector down) {
    public static ItemFrameGrid forFace(BlockFace face) {
        return switch (face) {
            case NORTH -> new ItemFrameGrid(new GridVector(-1, 0, 0), new GridVector(0, -1, 0));
            case SOUTH -> new ItemFrameGrid(new GridVector(1, 0, 0), new GridVector(0, -1, 0));
            case EAST -> new ItemFrameGrid(new GridVector(0, 0, -1), new GridVector(0, -1, 0));
            case WEST -> new ItemFrameGrid(new GridVector(0, 0, 1), new GridVector(0, -1, 0));
            case UP -> new ItemFrameGrid(new GridVector(1, 0, 0), new GridVector(0, 0, 1));
            case DOWN -> new ItemFrameGrid(new GridVector(1, 0, 0), new GridVector(0, 0, -1));
            default -> throw new IllegalArgumentException("item frame face must be cardinal: " + face);
        };
    }

    public GridVector offset(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) throw new IllegalArgumentException("tile coordinates must be non-negative");
        return right.multiply(tileX).add(down.multiply(tileY));
    }

    /** Offset from a clicked member back to the visible top-left anchor. */
    public GridVector anchorOffsetForMember(int tileX, int tileY) {
        return offset(tileX, tileY).multiply(-1);
    }
}
