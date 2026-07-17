package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;
import dev.tobyscamera.common.video.VideoFrameRate;
import java.util.Objects;
import java.util.function.Consumer;

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
    private Consumer<ViewfinderSettings> settingsListener = ignored -> { };

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

    /** Restores the confirmation screen when the client could not start an upload. */
    public boolean cancelUpload() {
        if (state != ViewfinderState.UPLOADING) return false;
        state = ViewfinderState.PREVIEW;
        return true;
    }

    public void finishUpload() { if (state == ViewfinderState.UPLOADING) state = ViewfinderState.VIEWFINDER; }
    public CaptureMode toggleMode() { if (state == ViewfinderState.VIEWFINDER) { mode = mode.next(); settingsChanged(); } return mode; }
    public int adjustVideoFps(int delta, int maximum) { videoFps = VideoFrameRate.next(videoFps, delta, maximum); settingsChanged(); return videoFps; }
    public int setVideoFps(int value, int maximum) { videoFps = VideoFrameRate.isSupported(value) && value <= maximum ? value : VideoFrameRate.clampToMaximum(maximum); settingsChanged(); return videoFps; }
    public int capVideoFps(int maximum) { videoFps = VideoFrameRate.clampToMaximum(maximum); settingsChanged(); return videoFps; }
    /** Applies a changed camera limit without discarding the user's lower selected frame rate. */
    public int clampVideoFpsToMaximum(int maximum) { videoFps = VideoFrameRate.next(videoFps, 0, maximum); settingsChanged(); return videoFps; }
    public CompositionGrid cycleGrid() { grid = grid.next(); settingsChanged(); return grid; }
    public void adjustZoom(double scrollDelta) { targetZoom = Math.clamp(targetZoom + (float) scrollDelta * 0.25f, MIN_ZOOM, MAX_ZOOM); settingsChanged(); }
    public void setZoom(float value) { targetZoom = Math.clamp(value, MIN_ZOOM, MAX_ZOOM); settingsChanged(); }
    public void setRollDegrees(float value) { composition = composition.withRollDegrees(value); settingsChanged(); }
    public void setAspectRatio(AspectRatio value) { composition = composition.withAspectRatio(value); settingsChanged(); }
    public ViewfinderSettings settings() { return new ViewfinderSettings(grid, targetZoom, composition, mode, videoFps); }
    public void applySettings(ViewfinderSettings settings) {
        settings = Objects.requireNonNull(settings, "settings");
        grid = settings.grid(); targetZoom = settings.zoom(); composition = settings.composition(); mode = settings.mode(); videoFps = settings.videoFps();
    }
    public void setSettingsListener(Consumer<ViewfinderSettings> listener) { settingsListener = Objects.requireNonNull(listener, "listener"); }
    private void settingsChanged() { settingsListener.accept(settings()); }
    public ViewfinderState state() { return state; }
    public CompositionGrid grid() { return grid; }
    public float targetZoom() { return targetZoom; }
    public CameraComposition composition() { return composition; }
    public int gridSize() { return gridSize; }
    public CaptureMode mode() { return mode; }
    public int videoFps() { return videoFps; }
    public boolean captureHidden() { return state == ViewfinderState.CAPTURING && mode == CaptureMode.PHOTO; }
    public boolean zoomActive() { return state == ViewfinderState.VIEWFINDER || state == ViewfinderState.CAPTURING; }
}
