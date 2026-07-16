package dev.tobyscamera.folia.config;

import java.util.Map;

public record PluginSettings(
        String cameraTagKey,
        String filmTagKey,
        int tokenTtlSeconds,
        int perSecond,
        int perMinute,
        int maxGridSize,
        int chunkBytes,
        int uploadTimeoutSeconds,
        int videoMaxFps,
        int videoMaxFrames,
        int videoUploadChunksPerSecond,
        int videoMaxActiveMapFrames,
        String invalidTokenKickMessage) {

    public static PluginSettings from(Map<String, ?> values) {
        PluginSettings settings = new PluginSettings(
                string(values, "camera-tag-key", "tobyscamera:camera"),
                string(values, "film-tag-key", "tobyscamera:film"),
                integer(values, "token-ttl-seconds", 60),
                integer(values, "rate-limit.per-second", 1),
                integer(values, "rate-limit.per-minute", 12),
                integer(values, "upload.max-grid-size", 4),
                integer(values, "upload.chunk-bytes", 8_192),
                integer(values, "upload.timeout-seconds", 30),
                integer(values, "video.max-fps", 10),
                integer(values, "video.max-frames", 100),
                integer(values, "video.max-upload-chunks-per-second", 120),
                integer(values, "video.max-active-map-frames", 128),
                string(values, "invalid-token.kick-message", "Invalid or expired photo upload token"));
        settings.validate();
        return settings;
    }

    private void validate() {
        if (!cameraTagKey.contains(":")) throw new IllegalArgumentException("camera-tag-key must be namespaced");
        if (!filmTagKey.contains(":")) throw new IllegalArgumentException("film-tag-key must be namespaced");
        if (tokenTtlSeconds < 1 || perSecond < 1 || perMinute < 1 || uploadTimeoutSeconds < 1) {
            throw new IllegalArgumentException("durations and rate limits must be positive");
        }
        if (maxGridSize < 1) throw new IllegalArgumentException("max-grid-size must be positive");
        if (chunkBytes < 1 || chunkBytes > 8_192) throw new IllegalArgumentException("chunk-bytes must be 1..8192");
        if (videoMaxFps < 1 || videoMaxFps > 20 || videoMaxFrames < 1 || videoUploadChunksPerSecond < 1 || videoMaxActiveMapFrames < 1)
            throw new IllegalArgumentException("video limits are invalid");
    }

    private static String string(Map<String, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }

    private static int integer(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }
}
