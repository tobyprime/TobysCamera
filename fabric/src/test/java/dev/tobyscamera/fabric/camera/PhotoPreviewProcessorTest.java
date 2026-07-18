package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    @Test
    void producesTheBagPreviewByRenderingTheWholePrintCanvasAtOneByOne() {
        BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int shade = (x * 255 / 255 + y) / 2;
            image.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade);
        }
        CapturedFrame frame = new CapturedFrame(image, 2, new CameraComposition(new AspectRatio(2, 1), 0.0f));
        MapTileEncoder encoder = new MapTileEncoder();

        PhotoPreviewProcessor.Result result = new PhotoPreviewProcessor().process(frame, 2, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        assertArrayEquals(encoder.bagPreview(image, MapTileEncoder.DitheringMode.FLOYD_STEINBERG), result.bagPreview());
    }
}
