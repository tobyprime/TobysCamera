package dev.tobyscamera.fabric.viewfinder;

/** Detects the second shutter press that ends an active video recording. */
public final class VideoShutterTransition {
    private VideoShutterTransition() { }

    public static boolean stopsRecording(ViewfinderState before, CaptureMode mode, ViewfinderState after) {
        return before == ViewfinderState.CAPTURING && mode == CaptureMode.VIDEO && after == ViewfinderState.PREVIEW;
    }
}
