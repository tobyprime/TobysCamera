package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.fabric.camera.AspectRatio;
import org.junit.jupiter.api.Test;

class ViewfinderControlModelTest {
    @Test
    void exposesAllBottomControlValuesAndAppliesThemToTheLiveViewfinder() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        ViewfinderControlModel controls = new ViewfinderControlModel(session);

        controls.setRollDegrees(123.5f);
        controls.setZoom(8.0f);
        controls.nextRatio();
        controls.cycleGrid();

        assertEquals(123.5f, session.composition().rollDegrees());
        assertEquals(4.0f, session.targetZoom());
        assertEquals(AspectRatio.of(4, 3), session.composition().aspectRatio());
        assertEquals(CompositionGrid.THIRDS, session.grid());
    }

    @Test
    void acceptsCustomRatioAndOnlyShowsFpsControlsInVideoMode() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        ViewfinderControlModel controls = new ViewfinderControlModel(session);

        assertTrue(controls.setCustomRatio("2:3"));
        assertEquals(AspectRatio.of(2, 3), session.composition().aspectRatio());
        assertTrue(!controls.showsVideoFps());
        session.toggleMode();
        assertTrue(controls.showsVideoFps());
        assertEquals(20, controls.adjustVideoFps(10, 20));
        assertEquals(1, controls.setVideoFps(1, 20));
    }
}
