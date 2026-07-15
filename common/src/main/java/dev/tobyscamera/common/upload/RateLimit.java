package dev.tobyscamera.common.upload;

public record RateLimit(int perSecond, int perMinute) {
    public RateLimit {
        if (perSecond < 1 || perMinute < 1) {
            throw new IllegalArgumentException("rate limits must be positive");
        }
    }
}
