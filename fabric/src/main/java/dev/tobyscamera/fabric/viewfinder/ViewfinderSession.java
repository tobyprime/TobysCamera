package dev.tobyscamera.fabric.viewfinder;

public final class ViewfinderSession {
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private ViewfinderState state = ViewfinderState.CLOSED;
    private CompositionGrid grid = CompositionGrid.NONE;
    private float targetZoom = MIN_ZOOM;
    private int gridSize;

    public boolean open() {
        if (state != ViewfinderState.CLOSED) return false;
        state = ViewfinderState.VIEWFINDER;
        return true;
    }

    public void close() { state = ViewfinderState.CLOSED; gridSize = 0; }

    public boolean pressShutter() {
        if (state != ViewfinderState.VIEWFINDER) return false;
        state = ViewfinderState.AWAITING_GRANT;
        return true;
    }

    public boolean acceptGrant(int gridSize) {
        if (state != ViewfinderState.AWAITING_GRANT || gridSize < 1 || gridSize > 4) return false;
        this.gridSize = gridSize;
        state = ViewfinderState.CAPTURING;
        return true;
    }

    public boolean rejectGrant() {
        if (state != ViewfinderState.AWAITING_GRANT) return false;
        state = ViewfinderState.VIEWFINDER;
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
    public CompositionGrid cycleGrid() { grid = grid.next(); return grid; }
    public void adjustZoom(double scrollDelta) { targetZoom = Math.clamp(targetZoom + (float) scrollDelta * 0.25f, MIN_ZOOM, MAX_ZOOM); }
    public ViewfinderState state() { return state; }
    public CompositionGrid grid() { return grid; }
    public float targetZoom() { return targetZoom; }
    public int gridSize() { return gridSize; }
    public boolean captureHidden() { return state == ViewfinderState.CAPTURING; }
    public boolean zoomActive() { return state == ViewfinderState.VIEWFINDER || state == ViewfinderState.AWAITING_GRANT; }
}
