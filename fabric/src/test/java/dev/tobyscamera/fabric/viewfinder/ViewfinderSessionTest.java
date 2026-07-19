package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewfinderSessionTest {
    @Test
    void transitionsFromViewfinderToPhotoCaptureAndPreview() {
        ViewfinderSession session = new ViewfinderSession();

        assertTrue(session.open());
        assertTrue(session.pressShutter(2));
        assertEquals(ViewfinderState.CAPTURING, session.state());
        assertTrue(session.captureComplete());
        assertEquals(ViewfinderState.PREVIEW, session.state());
    }

    @Test
    void shutterRejectsInvalidOrDuplicatePhotoCaptureRequests() {
        ViewfinderSession session = new ViewfinderSession();
        session.open();

        assertFalse(session.pressShutter(0));
        assertTrue(session.pressShutter(1));
        assertFalse(session.pressShutter(1));
    }
}
