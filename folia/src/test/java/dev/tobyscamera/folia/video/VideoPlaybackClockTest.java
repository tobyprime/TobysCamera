package dev.tobyscamera.folia.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class VideoPlaybackClockTest { @Test void loopsUsingElapsedTimeWithoutTickDrift() { var clock=new VideoPlaybackClock(1_000L); assertEquals(0,clock.frameAt(3,10,1_000)); assertEquals(1,clock.frameAt(3,10,1_100)); assertEquals(2,clock.frameAt(3,10,1_200)); assertEquals(0,clock.frameAt(3,10,1_300)); } }
