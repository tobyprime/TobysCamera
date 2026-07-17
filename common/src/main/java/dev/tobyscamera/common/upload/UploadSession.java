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
    private final byte[] previewPixels = new byte[TILE_BYTES];
    private int previewReceived;

    public UploadSession(UploadGrant grant, int width, int height) {
        if (width < 1 || height < 1 || width > grant.gridSize() || height > grant.gridSize()) {
            throw new UploadFailure("grid exceeds grant");
        }
        this.grant = grant;
        this.width = width;
        this.height = height;
        this.tiles = new byte[width * height][TILE_BYTES];
        this.received = new int[width * height];
    }

    public synchronized void append(UUID playerId, int tileX, int tileY, int offset, byte[] bytes) {
        if (!grant.playerId().equals(playerId)) throw new UploadFailure("grant belongs to another player");
        if (!previewComplete()) throw new UploadFailure("preview is incomplete");
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) throw new UploadFailure("tile is outside grid");
        int index = tileY * width + tileX;
        if (offset != received[index]) throw new UploadFailure("tile chunks must be contiguous");
        if (bytes.length == 0 || bytes.length > TILE_BYTES - offset) throw new UploadFailure("tile length is invalid");
        System.arraycopy(bytes, 0, tiles[index], offset, bytes.length);
        received[index] += bytes.length;
    }

    public synchronized boolean isComplete() {
        return previewComplete() && Arrays.stream(received).allMatch(value -> value == TILE_BYTES);
    }

    public synchronized void appendPreview(UUID playerId, int offset, byte[] bytes) {
        if (!grant.playerId().equals(playerId)) throw new UploadFailure("grant belongs to another player");
        if (offset != previewReceived) throw new UploadFailure("preview chunks must be contiguous");
        if (bytes.length == 0 || bytes.length > TILE_BYTES - offset) throw new UploadFailure("preview length is invalid");
        System.arraycopy(bytes, 0, previewPixels, offset, bytes.length);
        previewReceived += bytes.length;
    }

    public synchronized boolean previewComplete() {
        return previewReceived == TILE_BYTES;
    }

    public synchronized byte[] previewPixels() {
        if (!previewComplete()) throw new UploadFailure("preview is incomplete");
        return previewPixels.clone();
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
