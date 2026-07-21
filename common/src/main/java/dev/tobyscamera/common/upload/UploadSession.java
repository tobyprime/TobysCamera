package dev.tobyscamera.common.upload;

import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

public final class UploadSession {
    public static final int TILE_BYTES = 128 * 128;
    public static final int MAX_CHUNK_BYTES = 8_192;
    public static final int COVERAGE_BYTES = TILE_BYTES / Byte.SIZE;
    public static final int RESERVED_BYTES_PER_IMAGE = TILE_BYTES + COVERAGE_BYTES;
    private final UploadGrant grant;
    private final int width;
    private final int height;
    private final byte[][] tiles;
    private final int[] received;
    private final BitSet[] covered;
    private final byte[] previewPixels = new byte[TILE_BYTES];
    private final BitSet previewCovered = new BitSet(TILE_BYTES);
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
        this.covered = new BitSet[width * height];
        Arrays.setAll(covered, ignored -> new BitSet(TILE_BYTES));
    }

    public synchronized void append(UUID playerId, int tileX, int tileY, int offset, byte[] bytes) {
        if (!grant.playerId().equals(playerId)) throw new UploadFailure("grant belongs to another player");
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) throw new UploadFailure("tile is outside grid");
        int index = tileY * width + tileX;
        received[index] += appendRange(tiles[index], covered[index], offset, bytes);
    }

    public synchronized boolean isComplete() {
        return previewComplete() && Arrays.stream(received).allMatch(value -> value == TILE_BYTES);
    }

    public synchronized void appendPreview(UUID playerId, int offset, byte[] bytes) {
        if (!grant.playerId().equals(playerId)) throw new UploadFailure("grant belongs to another player");
        previewReceived += appendRange(previewPixels, previewCovered, offset, bytes);
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

    private static int appendRange(byte[] target, BitSet coverage, int offset, byte[] bytes) {
        if (offset < 0 || bytes.length < 1 || bytes.length > MAX_CHUNK_BYTES
                || offset > TILE_BYTES - bytes.length) {
            throw new UploadFailure("chunk range is invalid");
        }
        int end = offset + bytes.length;
        for (int index = coverage.nextSetBit(offset); index >= 0 && index < end;
                index = coverage.nextSetBit(index + 1)) {
            if (target[index] != bytes[index - offset]) {
                throw new UploadFailure("chunk overlaps conflicting data");
            }
        }
        int before = coverage.cardinality();
        System.arraycopy(bytes, 0, target, offset, bytes.length);
        coverage.set(offset, end);
        return coverage.cardinality() - before;
    }
}
