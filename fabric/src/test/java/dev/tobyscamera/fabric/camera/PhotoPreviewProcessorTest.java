package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PhotoPreviewProcessorTest {
    @Test
    void preparesTheExactMapPalettePreviewAndTilesAwayFromTheScreen() {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(20, 20, 0xffff4000);
        CapturedFrame frame = new CapturedFrame(image, 2);

        PhotoPreviewProcessor.Result result = new PhotoPreviewProcessor().process(frame, 2, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        assertEquals(4, result.photo().tiles().size());
        assertEquals(256, result.image().getWidth());
        assertEquals(256, result.image().getHeight());
    }
}
