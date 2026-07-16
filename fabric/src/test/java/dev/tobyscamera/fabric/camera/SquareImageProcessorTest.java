package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SquareImageProcessorTest {
    @Test
    void cropsTheExactCenterSquare() {
        BufferedImage source = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(50, 0, 0xff00ff00);

        CapturedFrame cropped = new CenterSquareCropProcessor().process(new CapturedFrame(source, 2));

        assertEquals(200, cropped.image().getWidth());
        assertEquals(200, cropped.image().getHeight());
        assertEquals(0xff00ff00, cropped.image().getRGB(0, 0));
    }

    @Test
    void resizesSquareToTheServerGrid() {
        CameraComposition composition = new CameraComposition(AspectRatio.of(4, 3), 15.0f);
        CapturedFrame frame = new CapturedFrame(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), 2, composition);

        CapturedFrame resized = new ResizeToGridProcessor().process(frame);

        assertEquals(256, resized.image().getWidth());
        assertEquals(256, resized.image().getHeight());
        assertEquals(composition, resized.composition());
    }

    @Test
    void cropsAndResizesToTheCapturedFourByThreeComposition() {
        CameraComposition composition = new CameraComposition(AspectRatio.of(4, 3), 0.0f);
        CapturedFrame frame = new CapturedFrame(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB), 4, composition);

        CapturedFrame cropped = new CenterSquareCropProcessor().process(frame);
        CapturedFrame resized = new ResizeToGridProcessor().process(cropped);

        assertEquals(267, cropped.image().getWidth());
        assertEquals(200, cropped.image().getHeight());
        assertEquals(512, resized.image().getWidth());
        assertEquals(384, resized.image().getHeight());
    }
}
