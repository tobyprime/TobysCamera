package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
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
}
