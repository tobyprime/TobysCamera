package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import java.util.List;

/** State changes shared by the bottom viewfinder controls. */
public final class ViewfinderControlModel {
    public static final List<AspectRatio> RATIOS = List.of(
            AspectRatio.of(1, 1), AspectRatio.of(4, 3), AspectRatio.of(3, 4),
            AspectRatio.of(3, 2), AspectRatio.of(2, 3), AspectRatio.of(16, 9), AspectRatio.of(9, 16));

    private final ViewfinderSession session;

    public ViewfinderControlModel(ViewfinderSession session) { this.session = session; }

    public void setRollDegrees(float value) { session.setRollDegrees(value); }
    public void setZoom(float value) { session.setZoom(value); }
    public AspectRatio nextRatio() {
        int current = RATIOS.indexOf(session.composition().aspectRatio());
        AspectRatio ratio = RATIOS.get((current + 1 + RATIOS.size()) % RATIOS.size());
        session.setAspectRatio(ratio);
        return ratio;
    }
    public boolean setCustomRatio(String value) {
        try {
            session.setAspectRatio(AspectRatio.parse(value));
            return true;
        } catch (IllegalArgumentException ignored) { return false; }
    }
    public CompositionGrid cycleGrid() { return session.cycleGrid(); }
    public boolean showsVideoFps() { return session.mode() == CaptureMode.VIDEO; }
    public int adjustVideoFps(int delta, int maximum) { return session.adjustVideoFps(delta, maximum); }
    public int setVideoFps(int value, int maximum) { return session.setVideoFps(value, maximum); }
}
