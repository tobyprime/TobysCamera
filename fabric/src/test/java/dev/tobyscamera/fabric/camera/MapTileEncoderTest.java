package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

class MapTileEncoderTest {
    private final MapTileEncoder encoder = new MapTileEncoder();

    @Test
    void defaultsToFloydSteinbergDitheringForCameraPostProcessing() {
        assertEquals(MapTileEncoder.DitheringMode.FLOYD_STEINBERG, MapTileEncoder.DEFAULT_DITHERING);
    }

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

    @Test
    void palettePreviewReconstructsTheExactEncodedTilePixels() {
        BufferedImage source = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xffa13e72);
        source.setRGB(127, 127, 0xff2fbb91);
        var encoded = encoder.encode(source, MapTileEncoder.DitheringMode.FLOYD_STEINBERG);

        BufferedImage preview = encoder.palettePreview(encoded);

        byte[] tile = encoded.tiles().getFirst();
        assertEquals(mapArgb(tile[0]), preview.getRGB(0, 0));
        assertEquals(mapArgb(tile[16_383]), preview.getRGB(127, 127));
    }

    @Test
    void neverEncodesOpaqueBlackAsAnyTransparentOrPaperSkippedMapColorVariant() {
        BufferedImage black = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) black.setRGB(x, y, 0xff000000);

        for (MapTileEncoder.DitheringMode mode : MapTileEncoder.DitheringMode.values()) {
            byte[] tile = encoder.encode(black, mode).tiles().getFirst();
            for (byte packedId : tile) {
                int id = Byte.toUnsignedInt(packedId);
                assertFalse(id < 4 || id >= 248);
            }
        }
    }

    @Test
    void encodesTransparentCanvasPixelsAsTheTransparentMapColor() {
        BufferedImage transparent = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

        for (MapTileEncoder.DitheringMode mode : MapTileEncoder.DitheringMode.values()) {
            byte[] tile = encoder.encode(transparent, mode).tiles().getFirst();
            for (byte packedId : tile) assertEquals(0, Byte.toUnsignedInt(packedId));
        }
    }

    private static boolean imagesEqual(BufferedImage left, BufferedImage right) {
        for (int y = 0; y < left.getHeight(); y++) for (int x = 0; x < left.getWidth(); x++) {
            if (left.getRGB(x, y) != right.getRGB(x, y)) return false;
        }
        return true;
    }

    private static int mapArgb(byte packedId) {
        return 0xff000000 | MapColor.getColorFromPackedId(Byte.toUnsignedInt(packedId)) & 0x00ffffff;
    }
}
