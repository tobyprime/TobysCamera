package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePhotoRepositoryTest {
    @TempDir Path directory;

    @Test
    void persistsTilesAndMapIdsAcrossRepositoryReopen() throws Exception {
        UUID photoId = UUID.randomUUID();
        Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
        Map<TileCoordinate, byte[]> pixels = new LinkedHashMap<>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y);
            maps.put(coordinate, 100 + y * 2 + x);
            byte[] tile = new byte[16_384]; tile[0] = (byte) (x + y * 2);
            pixels.put(coordinate, tile);
        }
        PhotoRecord record = new PhotoRecord(photoId, UUID.randomUUID(), Instant.parse("2026-07-16T00:00:00Z"), 2, 2, maps);

        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            repository.save(record, pixels, filled((byte) 91));
        }
        assertTrue(Files.exists(directory.resolve("photos").resolve(photoId.toString().substring(0, 2)).resolve(photoId + ".tbc")));
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            PhotoRecord restored = repository.loadAll().getFirst();
            assertEquals(record, restored);
            assertArrayEquals(pixels.get(new TileCoordinate(1, 1)), repository.readTile(photoId, new TileCoordinate(1, 1)));
            assertArrayEquals(filled((byte) 91), repository.readPreview(photoId));
        }
    }

    private static byte[] filled(byte value) {
        byte[] result = new byte[16_384];
        java.util.Arrays.fill(result, value);
        return result;
    }

}
