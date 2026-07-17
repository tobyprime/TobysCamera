package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

class NativeImageConverterTest {
    @Test
    void preservesArgbPixelsWhenCreatingANativeImage() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 0, 0xFF112233);

        try (NativeImage image = NativeImageConverter.fromBufferedImage(source)) {
            assertEquals(2, image.getWidth());
            assertEquals(1, image.getHeight());
            assertEquals(0xFF112233, NativePixelFormat.toArgb(image.getPixel(1, 0)));
        }
    }
}
