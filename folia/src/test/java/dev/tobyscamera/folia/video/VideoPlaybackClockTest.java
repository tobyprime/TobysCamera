package dev.tobyscamera.folia.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
