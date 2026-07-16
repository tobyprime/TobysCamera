package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.fabric.camera.AspectRatio;
import org.junit.jupiter.api.Test;

class VideoCaptureFormatTest {
    @Test
    void usesTheCameraMaximumLayoutAndDownscalesTheFramebufferBeforeDiskEncoding() {
        VideoCaptureFormat format = VideoCaptureFormat.forCamera(4, AspectRatio.of(4, 3), 1920, 1080);

        assertEquals(512, format.outputWidth());
        assertEquals(384, format.outputHeight());
        assertEquals(2, format.screenshotDownscale());
        assertEquals(720, format.cropWidth());
        assertEquals(540, format.cropHeight());
        assertEquals(120, format.cropLeft());
        assertEquals(0, format.cropTop());
    }

    @Test
    void preservesAPortraitViewfinderByCroppingTheDownscaledFramebuffer() {
        VideoCaptureFormat format = VideoCaptureFormat.forCamera(1, AspectRatio.of(2, 3), 1920, 1080);

        assertEquals(85, format.outputWidth());
        assertEquals(128, format.outputHeight());
        assertEquals(8, format.screenshotDownscale());
        assertEquals(90, format.cropWidth());
        assertEquals(135, format.cropHeight());
        assertEquals(75, format.cropLeft());
    }
}
