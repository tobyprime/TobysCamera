package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MapTileEncoderTest {
    private final MapTileEncoder encoder = new MapTileEncoder();

    @Test
    void encodesOneTileFor128Square() {
        var result = encoder.encode(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));
        assertEquals(1, result.gridWidth()); assertEquals(1, result.gridHeight()); assertEquals(16_384, result.tiles().getFirst().length);
    }

    @Test
    void encodesMinimumRequiredGridWithoutFourByFourCap() {
        var result = encoder.encode(new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB));
        assertEquals(5, result.gridWidth()); assertEquals(5, result.gridHeight()); assertEquals(25, result.tiles().size());
    }

    @Test
    void floydSteinbergDitheringChangesPaletteDistributionWithoutChangingTileLayout() {
        BufferedImage gradient = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int shade = 80 + x * 96 / 127;
            gradient.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade);
        }

        var plain = encoder.encode(gradient, MapTileEncoder.DitheringMode.OFF);
        var dithered = encoder.encode(gradient, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        assertEquals(plain.gridWidth(), dithered.gridWidth());
        assertEquals(plain.gridHeight(), dithered.gridHeight());
        assertEquals(16_384, dithered.tiles().getFirst().length);
        assertFalse(Arrays.equals(plain.tiles().getFirst(), dithered.tiles().getFirst()));
    }

    @Test
    void palettePreviewUsesTheSameDitheringChoiceAsTheUploadEncoding() {
        BufferedImage gradient = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int shade = 80 + x * 96 / 127;
            gradient.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade);
        }

        BufferedImage plain = encoder.palettePreview(gradient, MapTileEncoder.DitheringMode.OFF);
        BufferedImage dithered = encoder.palettePreview(gradient, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        assertEquals(128, dithered.getWidth());
        assertEquals(128, dithered.getHeight());
        assertFalse(imagesEqual(plain, dithered));
    }

    private static boolean imagesEqual(BufferedImage left, BufferedImage right) {
        for (int y = 0; y < left.getHeight(); y++) for (int x = 0; x < left.getWidth(); x++) {
            if (left.getRGB(x, y) != right.getRGB(x, y)) return false;
        }
        return true;
    }
}
