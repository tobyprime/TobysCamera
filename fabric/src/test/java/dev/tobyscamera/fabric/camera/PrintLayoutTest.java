package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PrintLayoutTest {
    @Test
    void selectsFourByTwoMapsForSixteenByNineAtFourX() {
        PrintLayout layout = PrintLayout.forMaximumSide(4, AspectRatio.of(16, 9));

        assertEquals(4, layout.gridWidth());
        assertEquals(2, layout.gridHeight());
    }

    @Test
    void selectsFourByThreeMapsForThreeByTwoAtFourX() {
        PrintLayout layout = PrintLayout.forMaximumSide(4, AspectRatio.of(3, 2));

        assertEquals(4, layout.gridWidth());
        assertEquals(3, layout.gridHeight());
    }

    @Test
    void parsesCustomAspectRatio() {
        assertEquals(2.5, AspectRatio.parse("5:2").value());
    }

    @Test
    void containsSquarePhotoOnBlackCanvasWithoutCropping() {
        java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(java.awt.Color.RED);
        graphics.fillRect(0, 0, 4, 4);
        graphics.dispose();

        java.awt.image.BufferedImage result = new PrintCanvasProcessor().process(source, PrintLayout.forMaximumSide(4, AspectRatio.of(16, 9)));

        assertEquals(512, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(0xff000000, result.getRGB(0, 0));
        assertEquals(0xffff0000, result.getRGB(128, 0));
    }

    @Test
    void keepsPortraitCompositionInsideOneSquareMapWithBlackSideBars() {
        java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(java.awt.Color.RED);
        graphics.fillRect(0, 0, 4, 4);
        graphics.dispose();

        java.awt.image.BufferedImage result = new PrintCanvasProcessor().process(source, PrintLayout.forMaximumSide(1, AspectRatio.of(2, 3)));

        assertEquals(128, result.getWidth());
        assertEquals(128, result.getHeight());
        assertEquals(0xff000000, result.getRGB(0, 64));
        assertEquals(0xffff0000, result.getRGB(64, 64));
    }
}
