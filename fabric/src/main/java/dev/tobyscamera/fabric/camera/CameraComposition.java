package dev.tobyscamera.fabric.camera;

public record CameraComposition(AspectRatio aspectRatio, float rollDegrees) {
    public static final CameraComposition DEFAULT = new CameraComposition(AspectRatio.of(1, 1), 0.0f);

    public CameraComposition {
        if (aspectRatio == null) throw new IllegalArgumentException("aspect ratio is required");
        rollDegrees = normalize(rollDegrees);
    }

    public CameraComposition withAspectRatio(AspectRatio value) { return new CameraComposition(value, rollDegrees); }
    public CameraComposition withRollDegrees(float value) { return new CameraComposition(aspectRatio, value); }
    private static float normalize(float value) { return (float) (((value % 360.0f) + 360.0f) % 360.0f); }
}
