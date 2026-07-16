package dev.tobyscamera.folia.video;

import dev.tobyscamera.common.video.VideoFrameRate;

public final class VideoPlaybackClock {
    public boolean shouldUpdateAtTick(int fps, long serverTick) {
        if (!VideoFrameRate.isSupported(fps) || serverTick < 0) throw new IllegalArgumentException("invalid video playback values");
        return serverTick % (20 / fps) == 0;
    }

    public int frameAtTick(int frameCount, int fps, long serverTick) {
        if (frameCount < 1 || !VideoFrameRate.isSupported(fps) || serverTick < 0) throw new IllegalArgumentException("invalid video playback values");
        return (int) ((serverTick / (20 / fps)) % frameCount);
    }
}
