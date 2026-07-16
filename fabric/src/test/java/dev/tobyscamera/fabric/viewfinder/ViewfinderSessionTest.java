package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewfinderSessionTest {
    @Test
    void transitionsFromViewfinderToGrantAndPreview() {
        ViewfinderSession session = new ViewfinderSession();

        assertTrue(session.open());
        assertEquals(ViewfinderState.VIEWFINDER, session.state());
        assertTrue(session.pressShutter());
        assertEquals(ViewfinderState.AWAITING_GRANT, session.state());
        assertTrue(session.acceptGrant(2));
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
    void grantCanOnlyBeAcceptedWhileAwaiting() {
        ViewfinderSession session = new ViewfinderSession();
        assertFalse(session.acceptGrant(1));
        session.open();
        session.pressShutter();
        assertTrue(session.acceptGrant(1));
        assertFalse(session.acceptGrant(1));
    }

    @Test
    void keepsZoomActiveUntilTheCaptureFrameIsRendered() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();
        assertTrue(session.zoomActive());
        session.pressShutter();
        assertTrue(session.zoomActive());
        session.acceptGrant(1);
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
}
