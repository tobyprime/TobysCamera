package dev.tobyscamera.fabric.video;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.NativeImageConverter;
import java.awt.image.BufferedImage;
import java.io.IOException;

final class VideoTestImages {
    private VideoTestImages() { }

    static void append(TemporaryVideoRecording recording, BufferedImage source) throws IOException {
        try (NativeImage image = NativeImageConverter.fromBufferedImage(source)) {
            recording.appendNativeImage(image);
        }
    }
}
