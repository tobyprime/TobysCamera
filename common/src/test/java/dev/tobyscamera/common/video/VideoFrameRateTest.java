package dev.tobyscamera.common.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VideoFrameRateTest {
    @Test
    void listsOnlyFrameRatesAllowedByTheCameraLimit() {
        assertEquals(List.of(1, 5, 10), VideoFrameRate.valuesUpTo(12));
    }
    @Test
    void selectsTheNearestSupportedRateThatDoesNotExceedActualCaptureRate() {
        assertEquals(10, VideoFrameRate.measured(50, 5_000L, 20));
        assertEquals(5, VideoFrameRate.measured(30, 5_000L, 20));
    }
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
