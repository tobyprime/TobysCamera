package dev.tobyscamera.fabric.camera;

/** Monotonic non-blocking allowance for photo upload chunks. */
public final class ChunkTokenBucket {
    private final int perSecond;
    private final int maximumBurst;
    private double tokens;
    private long lastMillis;

    public ChunkTokenBucket(int perSecond, long nowMillis) {
        if (perSecond < 1) throw new IllegalArgumentException("chunk rate must be positive");
        this.perSecond = perSecond;
        this.maximumBurst = Math.max(1, Math.min(perSecond, (perSecond + 19) / 20));
        this.tokens = 0;
        this.lastMillis = nowMillis;
    }

    public int takeAvailable(long nowMillis, int requested) {
        if (requested < 1) return 0;
        if (nowMillis > lastMillis) {
            tokens = Math.min(maximumBurst, tokens + (nowMillis - lastMillis) * perSecond / 1_000.0);
            lastMillis = nowMillis;
        }
        int allowed = Math.min(requested, (int) tokens);
        tokens -= allowed;
        return allowed;
    }
}
