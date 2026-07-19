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
    void producesTheSameUndistortedThreeByTwoBagPreviewAtEveryPrintSize() {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int shade = (x * 255 / (image.getWidth() - 1) + y * 255 / (image.getHeight() - 1)) / 2;
            image.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade);
        }
        CapturedFrame frame = new CapturedFrame(image, 3, new CameraComposition(new AspectRatio(3, 2), 0.0f));
        MapTileEncoder encoder = new MapTileEncoder();
        PhotoPreviewProcessor processor = new PhotoPreviewProcessor();

        PhotoPreviewProcessor.Result twoByTwo = processor.process(frame, 2, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);
        PhotoPreviewProcessor.Result threeByThree = processor.process(frame, 3, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);
        var oneByOneCanvas = new PrintCanvasProcessor().process(image, PrintLayout.forMaximumSide(1, frame.composition().aspectRatio()));
        byte[] expected = encoder.bagPreview(oneByOneCanvas, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        assertArrayEquals(expected, twoByTwo.bagPreview());
        assertArrayEquals(expected, threeByThree.bagPreview());
    }
}
