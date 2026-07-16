package dev.tobyscamera.folia.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
class VideoPlaybackClockTest {
    @Test void loopsOnExactServerTickBoundaries() {
        var clock = new VideoPlaybackClock();
        assertEquals(0, clock.frameAtTick(3, 10, 0));
        assertEquals(0, clock.frameAtTick(3, 10, 1));
        assertEquals(1, clock.frameAtTick(3, 10, 2));
        assertEquals(2, clock.frameAtTick(3, 10, 4));
        assertEquals(0, clock.frameAtTick(3, 10, 6));
    }

    @Test void onlyUpdatesOnTheSelectedFrameRateBoundary() {
        var clock = new VideoPlaybackClock();
        assertTrue(clock.shouldUpdateAtTick(10, 0));
        assertFalse(clock.shouldUpdateAtTick(10, 1));
        assertTrue(clock.shouldUpdateAtTick(10, 2));
        assertFalse(clock.shouldUpdateAtTick(10, 3));
        assertTrue(clock.shouldUpdateAtTick(5, 4));
        assertFalse(clock.shouldUpdateAtTick(5, 5));
    }
}
