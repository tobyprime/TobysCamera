package dev.tobyscamera.common.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VideoFrameRateTest {
    @Test
    void exposesOnlyTheFourTickAlignedRates() {
        assertTrue(VideoFrameRate.isSupported(1));
        assertTrue(VideoFrameRate.isSupported(5));
        assertTrue(VideoFrameRate.isSupported(10));
        assertTrue(VideoFrameRate.isSupported(20));
        assertFalse(VideoFrameRate.isSupported(2));
        assertEquals(5, VideoFrameRate.clampToMaximum(6));
        assertEquals(20, VideoFrameRate.next(10, 1, 20));
        assertEquals(5, VideoFrameRate.next(10, -1, 20));
    }
}
