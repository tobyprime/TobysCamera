package dev.tobyscamera.common.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadSessionTest {
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TOKEN = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void acceptsContiguousChunksAndCompletesEveryTile() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.parse("2026-07-16T00:00:00Z"), Instant.parse("2026-07-16T00:01:00Z"), 2), 2, 2);
        byte[] first = new byte[8_192];
        byte[] second = new byte[8_192];
        second[0] = 42;

        session.appendPreview(PLAYER, 0, first);
        session.appendPreview(PLAYER, 8_192, second);

        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                session.append(PLAYER, x, y, 0, first);
                session.append(PLAYER, x, y, 8_192, second);
            }
        }

        assertTrue(session.isComplete());
        assertTrue(session.previewComplete());
        assertArrayEquals(second, java.util.Arrays.copyOfRange(session.previewPixels(), 8_192, 16_384));
        assertArrayEquals(second, java.util.Arrays.copyOfRange(session.tile(1, 1), 8_192, 16_384));
    }

    @Test
    void acceptsTileAndPreviewChunksInAnyOrder() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] first = filled((byte) 17, 8_192);
        byte[] second = filled((byte) 23, 8_192);

        session.append(PLAYER, 0, 0, 8_192, second);
        session.appendPreview(PLAYER, 8_192, second);
        assertFalse(session.isComplete());
        session.append(PLAYER, 0, 0, 0, first);
        session.appendPreview(PLAYER, 0, first);

        assertTrue(session.isComplete());
        assertArrayEquals(second, Arrays.copyOfRange(session.previewPixels(), 8_192, 16_384));
        assertArrayEquals(second, Arrays.copyOfRange(session.tile(0, 0), 8_192, 16_384));
    }

    @Test
    void acceptsIdenticalDuplicateAndOverlappingChunksWithoutAdvancingCoverageTwice() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] first = filled((byte) 31, 8_192);
        byte[] second = filled((byte) 47, 8_192);

        session.appendPreview(PLAYER, 0, first);
        session.appendPreview(PLAYER, 0, first.clone());
        session.appendPreview(PLAYER, 4_096, Arrays.copyOfRange(first, 4_096, 8_192));
        session.appendPreview(PLAYER, 8_192, second);
        session.append(PLAYER, 0, 0, 0, first);
        session.append(PLAYER, 0, 0, 0, first.clone());
        session.append(PLAYER, 0, 0, 4_096, Arrays.copyOfRange(first, 4_096, 8_192));
        session.append(PLAYER, 0, 0, 8_192, second);

        assertTrue(session.isComplete());
    }

    @Test
    void rejectsConflictingOverlapAndInvalidChunkRanges() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] first = filled((byte) 61, 8_192);
        session.appendPreview(PLAYER, 0, first);
        byte[] conflict = Arrays.copyOfRange(first, 4_096, 8_192);
        conflict[0] ^= 1;

        UploadFailure overlap = assertThrows(UploadFailure.class,
                () -> session.appendPreview(PLAYER, 4_096, conflict));
        assertEquals("chunk overlaps conflicting data", overlap.getMessage());
        assertThrows(UploadFailure.class, () -> session.appendPreview(PLAYER, -1, new byte[] {1}));
        assertThrows(UploadFailure.class, () -> session.appendPreview(PLAYER, 8_192, new byte[0]));
        assertThrows(UploadFailure.class, () -> session.appendPreview(PLAYER, 8_192, new byte[8_193]));
        assertThrows(UploadFailure.class, () -> session.appendPreview(PLAYER, 16_384, new byte[] {1}));
        assertFalse(session.isComplete());
    }

    @Test
    void rejectsForeignPlayerAndOutOfRangeTileCoordinates() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] bytes = new byte[8_192];

        assertThrows(UploadFailure.class, () -> session.append(UUID.randomUUID(), 0, 0, 0, bytes));
        assertThrows(UploadFailure.class, () -> session.append(PLAYER, 1, 0, 0, bytes));
        assertFalse(session.isComplete());
    }

    @Test
    void rejectsUploadGridThatDoesNotExactlyMatchGrant() {
        UploadGrant grant = new UploadGrant(TOKEN, PLAYER, Instant.EPOCH, Instant.MAX, 2);

        assertThrows(UploadFailure.class, () -> new UploadSession(grant, 1, 4));
        assertThrows(UploadFailure.class, () -> new UploadSession(grant, 4, 1));
    }

    @Test
    void acceptsRectangleWithinTheGrantedMaximum() {
        UploadGrant grant = new UploadGrant(TOKEN, PLAYER, Instant.now(), Instant.now().plusSeconds(30), 4);

        UploadSession session = new UploadSession(grant, 4, 2);

        assertEquals(4, session.width());
        assertEquals(2, session.height());
    }

    @Test
    void permitsGrantSizesLargerThanFour() {
        UploadGrant grant = new UploadGrant(TOKEN, PLAYER, Instant.now(), Instant.now().plusSeconds(30), 8);

        assertEquals(8, grant.gridSize());
    }

    private static byte[] filled(byte value, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }
}
