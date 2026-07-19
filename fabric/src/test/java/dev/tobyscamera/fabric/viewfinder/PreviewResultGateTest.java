package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewResultGateTest {
    @Test
    void hidesThePreviousPhotoUntilTheLatestPreviewRevisionFinishes() {
        PreviewResultGate<String> gate = new PreviewResultGate<>();
        int firstRevision = gate.request();
        assertTrue(gate.publish(firstRevision, "one-by-one"));
        assertEquals("one-by-one", gate.result());

        gate.request();

        assertFalse(gate.ready());
        assertNull(gate.result());
    }

    @Test
    void ignoresACompletedTaskForASupersededPreviewRevision() {
        PreviewResultGate<String> gate = new PreviewResultGate<>();
        int supersededRevision = gate.request();
        int currentRevision = gate.request();

        assertFalse(gate.publish(supersededRevision, "old"));
        assertTrue(gate.publish(currentRevision, "current"));
        assertEquals("current", gate.result());
    }
}
