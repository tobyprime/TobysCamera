package dev.tobyscamera.common.upload;

import java.util.Arrays;
import java.util.UUID;

public final class UploadSession {
    public static final int TILE_BYTES = 128 * 128;
    private final UploadGrant grant;
    private final int width;
    private final int height;
    private final byte[][] tiles;
    private final int[] received;

    public UploadSession(UploadGrant grant, int width, int height) {
        if (width != grant.gridSize() || height != grant.gridSize()) {
            throw new UploadFailure("grid must match grant");
        }
        this.grant = grant;
        this.width = width;
        this.height = height;
        this.tiles = new byte[width * height][TILE_BYTES];
        this.received = new int[width * height];
    }

    public synchronized void append(UUID playerId, int tileX, int tileY, int offset, byte[] bytes) {
        if (!grant.playerId().equals(playerId)) throw new UploadFailure("grant belongs to another player");
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) throw new UploadFailure("tile is outside grid");
        int index = tileY * width + tileX;
        if (offset != received[index]) throw new UploadFailure("tile chunks must be contiguous");
        if (bytes.length == 0 || bytes.length > TILE_BYTES - offset) throw new UploadFailure("tile length is invalid");
        System.arraycopy(bytes, 0, tiles[index], offset, bytes.length);
        received[index] += bytes.length;
    }

    public synchronized boolean isComplete() {
        return Arrays.stream(received).allMatch(value -> value == TILE_BYTES);
    }

    public synchronized byte[] tile(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) throw new UploadFailure("tile is outside grid");
        int index = y * width + x;
        if (received[index] != TILE_BYTES) throw new UploadFailure("tile is incomplete");
        return tiles[index].clone();
    }

    public UploadGrant grant() { return grant; }
    public int width() { return width; }
    public int height() { return height; }
}
