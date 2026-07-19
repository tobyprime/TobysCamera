package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;

/** Client-local settings that should survive a restart. */
public record ViewfinderSettings(CompositionGrid grid, float zoom, CameraComposition composition) {
    public static final ViewfinderSettings DEFAULT = new ViewfinderSettings(
            CompositionGrid.NONE, 1.0f, new CameraComposition(AspectRatio.of(1, 1), 0.0f));

    public ViewfinderSettings {
        if (grid == null || composition == null) throw new IllegalArgumentException("viewfinder settings are required");
        zoom = Math.clamp(zoom, 1.0f, 4.0f);
    }
}
