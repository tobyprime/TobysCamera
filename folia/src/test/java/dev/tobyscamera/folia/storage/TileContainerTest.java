package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileContainerTest {
    @TempDir Path directory;

    @Test
    void storesIndividuallyAddressableCompressedTilesInOneFile() throws Exception {
        byte[] first = filled((byte) 3);
        byte[] second = filled((byte) 7);
        Map<String, byte[]> tiles = new LinkedHashMap<>();
        tiles.put("0-0", first);
        tiles.put("1-0", second);
        Path container = directory.resolve("photo.tbc");

        Map<String, TileContainer.Range> ranges = TileContainer.write(container, tiles);

        assertArrayEquals(first, TileContainer.read(container, ranges.get("0-0")));
        assertArrayEquals(second, TileContainer.read(container, ranges.get("1-0")));
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[16_384];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
