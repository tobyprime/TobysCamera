package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class VideoEncoderTest {
    @Test
    void encodesEveryRetainedFrameAtTheSelectedRectangularPrintLayout() throws Exception {
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            recording.append(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            recording.append(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 1, 2), new PrintLayout(2, 1, new AspectRatio(2, 1)), MapTileEncoder.DitheringMode.OFF);

            assertEquals(2, encoder.frameCount());
            assertEquals(2, encoder.gridWidth());
            assertEquals(1, encoder.gridHeight());
            assertEquals(2, encoder.frame(1).tiles().size());
        }
    }
}
