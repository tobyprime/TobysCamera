package dev.tobyscamera.fabric.camera;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;

/** Converts Java preview images into Minecraft-owned native textures. */
public final class NativeImageConverter {
    private NativeImageConverter() { }

    public static NativeImage fromBufferedImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixel(x, y, NativePixelFormat.toAbgr(source.getRGB(x, y)));
            }
        }
        return image;
    }
}
