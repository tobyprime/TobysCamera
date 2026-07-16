package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;

public final class ViewfinderSession {
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private ViewfinderState state = ViewfinderState.CLOSED;
    private CompositionGrid grid = CompositionGrid.NONE;
    private float targetZoom = MIN_ZOOM;
    private CameraComposition composition = CameraComposition.DEFAULT;
    private int gridSize;
    private CaptureMode mode = CaptureMode.PHOTO;
    private int videoFps = 10;

    public boolean open() {
        if (state != ViewfinderState.CLOSED) return false;
        state = ViewfinderState.VIEWFINDER;
        return true;
    }

    public void close() { state = ViewfinderState.CLOSED; gridSize = 0; }

    public boolean pressShutter(int gridSize) {
        if (state == ViewfinderState.CAPTURING && mode == CaptureMode.VIDEO) {
            state = ViewfinderState.PREVIEW;
            return true;
        }
        if (state != ViewfinderState.VIEWFINDER || gridSize < 1) return false;
        this.gridSize = gridSize;
        state = ViewfinderState.CAPTURING;
        return true;
    }

    public boolean captureComplete() {
        if (state != ViewfinderState.CAPTURING) return false;
        state = ViewfinderState.PREVIEW;
        return true;
    }

    public boolean retake() {
        if (state != ViewfinderState.PREVIEW) return false;
        state = ViewfinderState.VIEWFINDER;
        return true;
    }

    public boolean beginUpload() {
        if (state != ViewfinderState.PREVIEW) return false;
        state = ViewfinderState.UPLOADING;
        return true;
    }

    public void finishUpload() { if (state == ViewfinderState.UPLOADING) state = ViewfinderState.VIEWFINDER; }
    public CaptureMode toggleMode() { if (state == ViewfinderState.VIEWFINDER) mode = mode.next(); return mode; }
    public int adjustVideoFps(int delta, int maximum) { videoFps = Math.clamp(videoFps + delta, 1, Math.max(1, maximum)); return videoFps; }
    public CompositionGrid cycleGrid() { grid = grid.next(); return grid; }
    public void adjustZoom(double scrollDelta) { targetZoom = Math.clamp(targetZoom + (float) scrollDelta * 0.25f, MIN_ZOOM, MAX_ZOOM); }
    public void setRollDegrees(float value) { composition = composition.withRollDegrees(value); }
    public void setAspectRatio(AspectRatio value) { composition = composition.withAspectRatio(value); }
    public ViewfinderState state() { return state; }
    public CompositionGrid grid() { return grid; }
    public float targetZoom() { return targetZoom; }
    public CameraComposition composition() { return composition; }
    public int gridSize() { return gridSize; }
    public CaptureMode mode() { return mode; }
    public int videoFps() { return videoFps; }
    public boolean captureHidden() { return state == ViewfinderState.CAPTURING; }
    public boolean zoomActive() { return state == ViewfinderState.VIEWFINDER || state == ViewfinderState.CAPTURING; }
}
