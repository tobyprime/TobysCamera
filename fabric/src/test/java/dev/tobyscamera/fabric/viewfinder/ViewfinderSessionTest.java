package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void clampsPrintSizeToAtLeastOneAndTheCameraMaximum() {
        ViewfinderSession session = new ViewfinderSession();

        session.setPrintSize(0, 6);
        assertEquals(1, session.printSize());

        session.setPrintSize(9, 6);
        assertEquals(6, session.printSize());

        session.setPrintSize(3, 0);
        assertEquals(1, session.printSize());
    }

    @Test
    void appliesThePersistedPrintSizeFromSettings() {
        ViewfinderSession session = new ViewfinderSession();

        session.applySettings(new ViewfinderSettings(CompositionGrid.THIRDS, 2.0f,
                new CameraComposition(AspectRatio.of(3, 2), 10.0f), 4));

        assertEquals(4, session.printSize());
    }

    @Test
    void notifiesTheSettingsListenerWhenPrintSizeChanges() {
        ViewfinderSession session = new ViewfinderSession();
        AtomicReference<ViewfinderSettings> saved = new AtomicReference<>();
        session.setSettingsListener(saved::set);

        session.setPrintSize(4, 6);

        assertEquals(4, saved.get().printSize());
    }
}
