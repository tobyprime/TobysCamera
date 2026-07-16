package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaptureServiceTest {
    @Test
    void waitsOneFullFrameBeforeCaptureBecomesReady() {
        CaptureService service = new CaptureService();
        service.requestAfterNextFrame(2);

        assertFalse(service.tick());
        assertTrue(service.tick());
        assertEquals(2, service.takeGridSize());
        assertFalse(service.tick());
    }

    @Test
    void doesNotExposeTheCaptureGridSizeBeforeTheDelayedFrame() {
        CaptureService service = new CaptureService();
        service.requestAfterNextFrame(2);

        assertFalse(service.captureReady());
        service.tick();
        assertFalse(service.captureReady());
        service.tick();
        assertTrue(service.captureReady());
    }
}
