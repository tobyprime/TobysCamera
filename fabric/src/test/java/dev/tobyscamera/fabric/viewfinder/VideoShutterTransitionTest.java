package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VideoShutterTransitionTest {
    @Test
    void identifiesTheSecondVideoShutterAsTheRecordingStopTransition() {
        assertTrue(VideoShutterTransition.stopsRecording(ViewfinderState.CAPTURING, CaptureMode.VIDEO, ViewfinderState.PREVIEW));
        assertFalse(VideoShutterTransition.stopsRecording(ViewfinderState.VIEWFINDER, CaptureMode.VIDEO, ViewfinderState.CAPTURING));
        assertFalse(VideoShutterTransition.stopsRecording(ViewfinderState.CAPTURING, CaptureMode.PHOTO, ViewfinderState.PREVIEW));
    }
}
