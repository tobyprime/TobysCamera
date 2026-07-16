package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

class TemporaryVideoRecordingTest {
    @Test
    void writesAndReadsFramesWithoutKeepingThemInMemory() throws Exception {
        var directory = Files.createTempDirectory("camera-video");
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(directory)) {
            BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB); image.setRGB(1, 0, 0xFF112233);
            recording.append(image);
            assertEquals(1, recording.frameCount());
            assertEquals(0xFF112233, recording.read(0).getRGB(1, 0));
        }
        assertEquals(0L, Files.list(directory).count());
    }

    @Test
    void removesAbandonedRecordingsAtClientStartup() throws Exception {
        var directory = Files.createTempDirectory("camera-video");
        Files.createDirectory(directory.resolve("abandoned"));
        TemporaryVideoRecording.cleanupAbandoned(directory);
        assertEquals(0L, Files.list(directory).count());
    }

    @Test
    void writesNativeFramesWithoutConvertingThemOnTheRenderThread() throws Exception {
        var directory = Files.createTempDirectory("camera-video");
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(directory); NativeImage image = new NativeImage(2, 1, false)) {
            image.setPixel(1, 0, 0xFF332211);
            recording.appendNativeImage(image);

            assertEquals(1, recording.frameCount());
            assertEquals(0xFF332211, recording.read(0).getRGB(1, 0));
        }
    }
}
