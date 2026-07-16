package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VideoFrameRangeTest {
    @Test
    void retainsBothInclusiveEndpoints() {
        VideoFrameRange range = new VideoFrameRange(2, 5, 10);
        assertEquals(4, range.count());
        assertEquals(2, range.startInclusive());
        assertEquals(5, range.endInclusive());
    }

    @Test
    void rejectsAnInvertedOrOutOfBoundsRange() {
        assertThrows(IllegalArgumentException.class, () -> new VideoFrameRange(5, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new VideoFrameRange(0, 10, 10));
    }
}
