package dev.tobyscamera.common.upload;

import java.util.Arrays;
import java.util.UUID;

public final class VideoUploadSession {
    private final UploadGrant grant; private final int width, height, fps, frameCount; private final byte[][] tiles; private final int[] received;
    private final byte[] previewPixels = new byte[UploadSession.TILE_BYTES]; private int previewReceived;
    public VideoUploadSession(UploadGrant grant, int width, int height, int fps, int frameCount) {
        if (width < 1 || height < 1 || width > grant.gridSize() || height > grant.gridSize() || fps < 1 || frameCount < 1) throw new UploadFailure("video dimensions exceed grant");
        this.grant = grant; this.width = width; this.height = height; this.fps = fps; this.frameCount = frameCount;
        int count = Math.multiplyExact(Math.multiplyExact(width, height), frameCount); tiles = new byte[count][UploadSession.TILE_BYTES]; received = new int[count];
    }
    public synchronized void append(UUID player, int frame, int x, int y, int offset, byte[] bytes) {
        if (!grant.playerId().equals(player)) throw new UploadFailure("grant belongs to another player");
        if (!previewComplete()) throw new UploadFailure("preview is incomplete");
        if (frame < 0 || frame >= frameCount || x < 0 || x >= width || y < 0 || y >= height) throw new UploadFailure("tile is outside video");
        int index = (frame * height + y) * width + x; if (offset != received[index] || bytes.length == 0 || bytes.length > UploadSession.TILE_BYTES - offset) throw new UploadFailure("tile chunks must be contiguous");
        System.arraycopy(bytes, 0, tiles[index], offset, bytes.length); received[index] += bytes.length;
    }
    public synchronized boolean isComplete() { return previewComplete() && Arrays.stream(received).allMatch(v -> v == UploadSession.TILE_BYTES); }
    public synchronized void appendPreview(UUID player, int offset, byte[] bytes) {
        if (!grant.playerId().equals(player)) throw new UploadFailure("grant belongs to another player");
        if (offset != previewReceived) throw new UploadFailure("preview chunks must be contiguous");
        if (bytes.length == 0 || bytes.length > UploadSession.TILE_BYTES - offset) throw new UploadFailure("preview length is invalid");
        System.arraycopy(bytes, 0, previewPixels, offset, bytes.length); previewReceived += bytes.length;
    }
    public synchronized boolean previewComplete() { return previewReceived == UploadSession.TILE_BYTES; }
    public synchronized byte[] previewPixels() {
        if (!previewComplete()) throw new UploadFailure("preview is incomplete");
        return previewPixels.clone();
    }
    public synchronized byte[] tile(int frame, int x, int y) {
        if (frame < 0 || frame >= frameCount || x < 0 || x >= width || y < 0 || y >= height) throw new UploadFailure("tile is outside video");
        int index = (frame * height + y) * width + x;
        if (received[index] != UploadSession.TILE_BYTES) throw new UploadFailure("tile is incomplete");
        return tiles[index].clone();
    }
    public int width() { return width; } public int height() { return height; } public int fps() { return fps; } public int frameCount() { return frameCount; }
}
