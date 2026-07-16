package dev.tobyscamera.folia.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.upload.UploadGrant;
import dev.tobyscamera.common.upload.VideoUploadSession;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqliteVideoRepositoryTest {
    @Test
    void savesFramesAndReadsTheirTiles() throws Exception {
        var player = UUID.randomUUID(); var video = UUID.randomUUID();
        var session = new VideoUploadSession(new UploadGrant(UUID.randomUUID(), player, Instant.EPOCH, Instant.ofEpochSecond(60), 1), 1, 1, 10, 2);
        session.append(player, 0, 0, 0, 0, new byte[16_384]); session.append(player, 1, 0, 0, 0, filled((byte) 7));
        var maps = new LinkedHashMap<TileCoordinate, Integer>(); maps.put(new TileCoordinate(0, 0), 42);
        try (var repository = new SqliteVideoRepository(Files.createTempDirectory("video-repository"))) {
            repository.save(new VideoRecord(video, player, Instant.EPOCH, 1, 1, 10, 2, maps), session);
            assertEquals(1, repository.loadAll().size());
            assertArrayEquals(filled((byte) 7), repository.readTile(video, 1, new TileCoordinate(0, 0)));
        }
    }
    private static byte[] filled(byte value) { byte[] result = new byte[16_384]; java.util.Arrays.fill(result, value); return result; }
}
