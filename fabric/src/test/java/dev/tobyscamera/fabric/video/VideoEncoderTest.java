package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class VideoEncoderTest {
    @Test
    void retainsOnlyTheMostRecentlyEncodedFrame() throws Exception {
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            VideoTestImages.append(recording, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            VideoTestImages.append(recording, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 1, 2),
                    new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);

            var first = encoder.frame(0);
            encoder.frame(1);

            assertNotSame(first, encoder.frame(0));
        }
    }

    @Test
    void encodesEveryRetainedFrameAtTheSelectedRectangularPrintLayout() throws Exception {
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            VideoTestImages.append(recording, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            VideoTestImages.append(recording, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 1, 2), new PrintLayout(2, 1, new AspectRatio(2, 1)), MapTileEncoder.DitheringMode.OFF);

            assertEquals(2, encoder.frameCount());
            assertEquals(2, encoder.gridWidth());
            assertEquals(1, encoder.gridHeight());
            assertEquals(2, encoder.frame(1).tiles().size());
        }
    }

    @Test
    void cropsRecordedFramesToTheSelectedViewfinderAspectRatio() throws Exception {
        BufferedImage raw = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < raw.getHeight(); y++) for (int x = 0; x < raw.getWidth(); x++)
            raw.setRGB(x, y, x < 50 || x >= 350 ? 0xFFFF0000 : 0xFF00FF00);
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            VideoTestImages.append(recording, raw);
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 0, 1),
                    new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);

            BufferedImage preview = new MapTileEncoder().palettePreview(encoder.frame(0));
            assertNotEquals(0xFFFF0000, preview.getRGB(0, 64));
            assertNotEquals(0xFFFF0000, preview.getRGB(127, 64));
        }
    }
}
