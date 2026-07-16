package dev.tobyscamera.fabric.viewfinder;

public final class CaptureService {
    private int delayedFrames = -1;
    private int gridSize;
    private boolean captureReady;

    public void requestAfterNextFrame(int gridSize) {
        if (gridSize < 1) throw new IllegalArgumentException("grid size must be positive");
        this.gridSize = gridSize;
        captureReady = false;
        delayedFrames = 1;
    }

    public boolean tick() {
        if (delayedFrames < 0) return false;
        if (delayedFrames-- > 0) return false;
        delayedFrames = -1;
        captureReady = true;
        return true;
    }

    public int takeGridSize() {
        if (!captureReady || gridSize == 0) throw new IllegalStateException("no capture ready");
        int result = gridSize;
        gridSize = 0;
        captureReady = false;
        return result;
    }

    public boolean captureReady() { return captureReady; }
}
