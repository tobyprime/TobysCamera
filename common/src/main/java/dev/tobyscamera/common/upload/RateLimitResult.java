package dev.tobyscamera.common.upload;

public record RateLimitResult(boolean allowed, long retryAfterMillis) {
    public static RateLimitResult permit() { return new RateLimitResult(true, 0); }
    public static RateLimitResult deny(long retryAfterMillis) { return new RateLimitResult(false, Math.max(1, retryAfterMillis)); }
}
