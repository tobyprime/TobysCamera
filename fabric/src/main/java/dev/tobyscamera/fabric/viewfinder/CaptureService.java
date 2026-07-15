package dev.tobyscamera.fabric.viewfinder;

public final class CaptureService {
    private int delayedFrames = -1;
    private int gridSize;

    public void requestAfterNextFrame(int gridSize) {
        if (gridSize < 1 || gridSize > 4) throw new IllegalArgumentException("grid size must be 1..4");
        this.gridSize = gridSize;
        delayedFrames = 1;
    }

    public boolean tick() {
        if (delayedFrames < 0) return false;
        if (delayedFrames-- > 0) return false;
        delayedFrames = -1;
        return true;
    }

    public int takeGridSize() {
        if (gridSize == 0) throw new IllegalStateException("no capture requested");
        int result = gridSize;
        gridSize = 0;
        return result;
    }
}
