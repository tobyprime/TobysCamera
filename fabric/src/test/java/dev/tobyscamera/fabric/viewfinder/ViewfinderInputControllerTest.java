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
        ViewfinderInputController controls = new ViewfinderInputController(session, requests::incrementAndGet);

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
        ViewfinderInputController controls = new ViewfinderInputController(session, () -> { });

        assertFalse(controls.close());
        session.open();
        assertTrue(controls.close());
        assertEquals(ViewfinderState.CLOSED, session.state());
    }

    @Test
    void suppressesVanillaAttackWhileTheViewfinderOwnsInput() {
        ViewfinderSession session = new ViewfinderSession();
        ViewfinderInputController controls = new ViewfinderInputController(session, () -> { });

        assertFalse(controls.suppressesVanillaAttack());
        session.open();
        assertTrue(controls.suppressesVanillaAttack());
        session.pressShutter();
        assertTrue(controls.suppressesVanillaAttack());
        session.close();
        assertFalse(controls.suppressesVanillaAttack());
    }
}
