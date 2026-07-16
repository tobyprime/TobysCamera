package dev.tobyscamera.folia.video;
public record VideoPlaybackClock(long startedAtMillis) { public int frameAt(int frameCount,int fps,long nowMillis){if(frameCount<1||fps<1)throw new IllegalArgumentException("video dimensions must be positive");return (int)(((nowMillis-startedAtMillis)*fps/1_000L)%frameCount);} }
