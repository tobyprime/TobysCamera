package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ViewfinderInputControllerTest {
    @Test
    void shutterRequestsCaptureOnlyFromTheOpenViewfinder() {
        ViewfinderSession session = new ViewfinderSession();
        AtomicInteger requests = new AtomicInteger();
        ViewfinderInputController controls = new ViewfinderInputController(session, () -> 1, ignored -> requests.incrementAndGet());

        assertFalse(controls.pressShutter());
        assertEquals(0, requests.get());
        session.open();
        assertTrue(controls.pressShutter());
        assertEquals(1, requests.get());
        assertFalse(controls.pressShutter());
    }

    @Test
    void escapeClosesAnOpenViewfinder() {
        ViewfinderSession session = new ViewfinderSession();
        ViewfinderInputController controls = new ViewfinderInputController(session, () -> 1, ignored -> { });

        assertFalse(controls.close());
        session.open();
        assertTrue(controls.close());
        assertEquals(ViewfinderState.CLOSED, session.state());
    }

}
