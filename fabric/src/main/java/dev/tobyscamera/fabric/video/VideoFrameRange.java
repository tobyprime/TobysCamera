package dev.tobyscamera.fabric.video;

/** Inclusive range of recorded frame indices selected for printing. */
public record VideoFrameRange(int startInclusive, int endInclusive, int totalFrames) {
    public VideoFrameRange {
        if (totalFrames < 1 || startInclusive < 0 || endInclusive < startInclusive || endInclusive >= totalFrames) {
            throw new IllegalArgumentException("invalid video frame range");
        }
    }

    public int count() { return endInclusive - startInclusive + 1; }
}
