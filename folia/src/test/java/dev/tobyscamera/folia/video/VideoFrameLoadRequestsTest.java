package dev.tobyscamera.folia.video;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoFrameLoadRequestsTest {
    @Test
    void acceptsOnlyOnePendingRequestForTheSameFrameTile() {
        VideoFrameLoadRequests requests = new VideoFrameLoadRequests();
        VideoFrameLoadRequests.Key key = new VideoFrameLoadRequests.Key(UUID.randomUUID(), 42, 7);

        assertTrue(requests.begin(key));
        assertFalse(requests.begin(key));
        requests.complete(key);
        assertTrue(requests.begin(key));
    }
}
