package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapPreviewEncoderTest {
    @Test
    void scalesTheWholeTileGridIntoOneMap() {
        byte[] left = filled((byte) 11);
        byte[] right = filled((byte) 22);
        byte[] preview = MapPreviewEncoder.encode(2, 1, coordinate -> Map.of(0, left, 1, right).get(coordinate.x()));

        assertEquals(11, Byte.toUnsignedInt(preview[64 * 128 + 31]));
        assertEquals(22, Byte.toUnsignedInt(preview[64 * 128 + 96]));
    }

    private static byte[] filled(byte value) { byte[] result = new byte[16_384]; java.util.Arrays.fill(result, value); return result; }
}
