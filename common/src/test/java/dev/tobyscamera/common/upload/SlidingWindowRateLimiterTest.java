package dev.tobyscamera.common.upload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {
    @Test
    void rejectsSecondCaptureInsidePerSecondWindowButAllowsLaterCapture() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new RateLimit(1, 12));
        UUID player = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");

        assertTrue(limiter.tryAcquire(player, now).allowed());
        assertFalse(limiter.tryAcquire(player, now.plusMillis(500)).allowed());
        assertTrue(limiter.tryAcquire(player, now.plusSeconds(1)).allowed());
    }

    @Test
    void rejectsCaptureAfterMinuteQuota() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new RateLimit(10, 2));
        UUID player = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");

        assertTrue(limiter.tryAcquire(player, now).allowed());
        assertTrue(limiter.tryAcquire(player, now.plusSeconds(2)).allowed());
        assertFalse(limiter.tryAcquire(player, now.plusSeconds(3)).allowed());
    }
}
