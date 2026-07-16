package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewfinderSessionTest {
    @Test
    void transitionsFromViewfinderStraightToCaptureAndPreview() {
        ViewfinderSession session = new ViewfinderSession();

        assertTrue(session.open());
        assertEquals(ViewfinderState.VIEWFINDER, session.state());
        assertTrue(session.pressShutter(2));
        assertEquals(ViewfinderState.CAPTURING, session.state());
        assertTrue(session.captureComplete());
        assertEquals(ViewfinderState.PREVIEW, session.state());
    }

    @Test
    void cyclesGridAndKeepsZoomWithinBounds() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();

        assertEquals(CompositionGrid.THIRDS, session.cycleGrid());
        assertEquals(CompositionGrid.CROSSHAIR, session.cycleGrid());
        assertEquals(CompositionGrid.NONE, session.cycleGrid());
        for (int index = 0; index < 100; index++) session.adjustZoom(1.0);
        assertEquals(4.0f, session.targetZoom());
        for (int index = 0; index < 100; index++) session.adjustZoom(-1.0);
        assertEquals(1.0f, session.targetZoom());
    }

    @Test
    void shutterRejectsInvalidLocalGridSize() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        assertFalse(session.pressShutter(0));
        assertTrue(session.pressShutter(1));
        assertFalse(session.pressShutter(1));
    }

    @Test
    void keepsZoomActiveUntilTheCaptureFrameIsRendered() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        assertTrue(session.zoomActive());
        session.pressShutter(1);
        assertTrue(session.zoomActive());
        assertTrue(session.zoomActive());
        session.captureComplete();
        assertFalse(session.zoomActive());
    }

    @Test
    void derivesFourByThreeViewfinderFrameFromTheSelectedAspectRatio() {
        ViewfinderOverlay.Frame frame = ViewfinderOverlay.frame(1600, 1000, AspectRatio.of(4, 3));

        assertEquals(1333, frame.width());
        assertEquals(1000, frame.height());
        assertEquals(133, frame.left());
        assertEquals(0, frame.top());
    }

    @Test
    void switchesToVideoModeAndCapsSelectedFps() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        assertEquals(CaptureMode.VIDEO, session.toggleMode());
        assertEquals(10, session.adjustVideoFps(20, 10));
        assertEquals(1, session.adjustVideoFps(-20, 10));
    }

    @Test
    void secondShutterStopsVideoRecordingAndOpensConfirmation() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        session.toggleMode();
        assertTrue(session.pressShutter(1));
        assertEquals(ViewfinderState.CAPTURING, session.state());

        assertTrue(session.pressShutter(1));

        assertEquals(ViewfinderState.PREVIEW, session.state());
    }
}
