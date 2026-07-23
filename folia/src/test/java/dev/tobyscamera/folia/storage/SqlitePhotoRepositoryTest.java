package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.common.protocol.PhotoPresentation;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePhotoRepositoryTest {
    @TempDir Path directory;

    @Test
    void reportsPhotoAndTileTotalsWithoutLoadingPayloads() throws Exception {
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            assertEquals(new PhotoStorageStats(0, 0), repository.stats());
            repository.save(record(2, 1), pixels(2, 1), filled((byte) 1));
            repository.save(record(1, 3), pixels(1, 3), filled((byte) 2));
            assertEquals(new PhotoStorageStats(2, 5), repository.stats());
        }
    }

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

    @Test
    void persistsOwnerNameAndMetadataAcrossRepositoryReopen() throws Exception {
        UUID photoId = UUID.randomUUID();
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world_nether", 12, 64, -7,
                Instant.parse("2026-07-20T12:34:56Z"), new PhotoPresentation("Sunset", "At the fortress", false, true, false));
        PhotoRecord record = new PhotoRecord(photoId, UUID.randomUUID(), "Toby", Instant.parse("2026-07-20T13:00:00Z"),
                1, 1, Map.of(new TileCoordinate(0, 0), 100), metadata);

        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            repository.save(record, pixels(1, 1), filled((byte) 4));
        }
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            assertEquals(record, repository.find(photoId));
        }
    }

    @Test
    void findsMatchingPhotosWithStableNewestAndOldestPagination() throws Exception {
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            PhotoRecord first = record("Toby", "11111111-0000-0000-0000-000000000001", "2026-07-20T00:00:00Z");
            PhotoRecord second = record("Other", "22222222-0000-0000-0000-000000000002", "2026-07-21T00:00:00Z");
            PhotoRecord third = record("toby", "33333333-0000-0000-0000-000000000003", "2026-07-22T00:00:00Z");
            repository.save(first, pixels(1, 1), filled((byte) 1));
            repository.save(second, pixels(1, 1), filled((byte) 2));
            repository.save(third, pixels(1, 1), filled((byte) 3));

            PhotoPage newest = repository.findPage(new PhotoQuery("TOBY", PhotoQuery.Sort.NEWEST, 0, 1));
            assertEquals(List.of(third), newest.records());
            assertTrue(newest.hasNext());
            assertEquals(List.of(first), repository.findPage(new PhotoQuery("toby", PhotoQuery.Sort.NEWEST, 1, 1)).records());
            assertFalse(repository.findPage(new PhotoQuery("toby", PhotoQuery.Sort.NEWEST, 1, 1)).hasNext());
            assertEquals(List.of(first, third), repository.findPage(new PhotoQuery("toby", PhotoQuery.Sort.OLDEST, 0, 2)).records());
            assertEquals(List.of(second), repository.findPage(new PhotoQuery(second.ownerId().toString().substring(0, 8), PhotoQuery.Sort.NEWEST, 0, 2)).records());
            assertEquals(List.of(first), repository.findPage(new PhotoQuery(first.photoId().toString().substring(0, 8), PhotoQuery.Sort.NEWEST, 0, 2)).records());
        }
    }

    @Test
    void treatsQueryWildcardsAsLiteralCharacters() throws Exception {
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            PhotoRecord literal = record("A%B_C!", "11111111-0000-0000-0000-000000000001", "2026-07-20T00:00:00Z");
            PhotoRecord other = record("AxxbZc!", "22222222-0000-0000-0000-000000000002", "2026-07-21T00:00:00Z");
            repository.save(literal, pixels(1, 1), filled((byte) 1));
            repository.save(other, pixels(1, 1), filled((byte) 2));

            assertEquals(List.of(literal), repository.findPage(new PhotoQuery("a%b_c!", PhotoQuery.Sort.NEWEST, 0, 2)).records());
        }
    }

    @Test
    void persistsUploadBlocksAndAllowsUnblock() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            repository.block(new UploadBlock(playerId, adminId, Instant.parse("2026-07-20T12:00:00Z")));
            assertTrue(repository.isBlocked(playerId));
        }
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            assertTrue(repository.isBlocked(playerId));
            repository.unblock(playerId);
            assertFalse(repository.isBlocked(playerId));
        }
    }

    @Test
    void deletesOnlyTargetPhotoDataAndShardedMediaFile() throws Exception {
        PhotoRecord target = record(1, 1);
        PhotoRecord retained = record(1, 1);
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            repository.save(target, pixels(1, 1), filled((byte) 1));
            repository.save(retained, pixels(1, 1), filled((byte) 2));
            Path targetContainer = directory.resolve("photos").resolve(target.photoId().toString().substring(0, 2)).resolve(target.photoId() + ".tbc");
            Path retainedContainer = directory.resolve("photos").resolve(retained.photoId().toString().substring(0, 2)).resolve(retained.photoId() + ".tbc");

            repository.delete(target.photoId());

            assertNull(repository.find(target.photoId()));
            assertFalse(Files.exists(targetContainer));
            assertEquals(retained.photoId(), repository.find(retained.photoId()).photoId());
            assertTrue(Files.exists(retainedContainer));
        }
    }

    private static byte[] filled(byte value) {
        byte[] result = new byte[16_384];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static PhotoRecord record(int width, int height) {
        Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            maps.put(new TileCoordinate(x, y), 100 + maps.size());
        }
        return new PhotoRecord(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), width, height, maps);
    }

    private static PhotoRecord record(String ownerName, String ownerId, String createdAt) {
        UUID photoId = UUID.randomUUID();
        return new PhotoRecord(photoId, UUID.fromString(ownerId), ownerName, Instant.parse(createdAt), 1, 1,
                Map.of(new TileCoordinate(0, 0), 100), null);
    }

    private static Map<TileCoordinate, byte[]> pixels(int width, int height) {
        Map<TileCoordinate, byte[]> result = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            result.put(new TileCoordinate(x, y), new byte[16_384]);
        }
        return result;
    }

}
