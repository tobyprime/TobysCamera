package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;

/** Client-local settings that should survive a restart. */
public record ViewfinderSettings(CompositionGrid grid, float zoom, CameraComposition composition, CaptureMode mode, int videoFps) {
    public static final ViewfinderSettings DEFAULT = new ViewfinderSettings(
            CompositionGrid.NONE, 1.0f, new CameraComposition(AspectRatio.of(1, 1), 0.0f), CaptureMode.PHOTO, 10);

    public ViewfinderSettings {
        if (grid == null || composition == null || mode == null) throw new IllegalArgumentException("viewfinder settings are required");
        zoom = Math.clamp(zoom, 1.0f, 4.0f);
    }
}
