package dev.tobyscamera.common.upload;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SlidingWindowRateLimiter {
    private static final long SECOND_MILLIS = 1_000L;
    private static final long MINUTE_MILLIS = 60_000L;
    private final RateLimit limit;
    private final Map<UUID, ArrayDeque<Long>> accepted = new HashMap<>();

    public SlidingWindowRateLimiter(RateLimit limit) {
        this.limit = limit;
    }

    public synchronized RateLimitResult tryAcquire(UUID playerId, Instant now) {
        long timestamp = now.toEpochMilli();
        ArrayDeque<Long> history = accepted.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        while (!history.isEmpty() && history.peekFirst() <= timestamp - MINUTE_MILLIS) {
            history.removeFirst();
        }
        int secondCount = 0;
        long oldestSecond = timestamp;
        for (long previous : history) {
            if (previous > timestamp - SECOND_MILLIS) {
                secondCount++;
                oldestSecond = Math.min(oldestSecond, previous);
            }
        }
        if (secondCount >= limit.perSecond()) {
            return RateLimitResult.deny(oldestSecond + SECOND_MILLIS - timestamp);
        }
        if (history.size() >= limit.perMinute()) {
            return RateLimitResult.deny(history.peekFirst() + MINUTE_MILLIS - timestamp);
        }
        history.addLast(timestamp);
        return RateLimitResult.permit();
    }
}
