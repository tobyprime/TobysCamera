package dev.tobyscamera.common.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
    void rejectsMediaUntilItsContiguousPreviewIsComplete() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] chunk = new byte[8_192];

        assertThrows(UploadFailure.class, () -> session.append(PLAYER, 0, 0, 0, chunk));
        assertFalse(session.isComplete());
        assertThrows(UploadFailure.class, () -> session.previewPixels());
        session.appendPreview(PLAYER, 0, chunk);
        assertFalse(session.previewComplete());
        assertThrows(UploadFailure.class, () -> session.appendPreview(PLAYER, 8_191, chunk));
        session.appendPreview(PLAYER, 8_192, chunk);
        assertTrue(session.previewComplete());
        session.append(PLAYER, 0, 0, 0, new byte[16_384]);
        assertTrue(session.isComplete());
    }

    @Test
    void rejectsForeignPlayerOutOfOrderAndOutOfRangeChunks() {
        UploadSession session = new UploadSession(new UploadGrant(TOKEN, PLAYER,
                Instant.EPOCH, Instant.MAX, 1), 1, 1);
        byte[] bytes = new byte[8_192];

        assertThrows(UploadFailure.class, () -> session.append(UUID.randomUUID(), 0, 0, 0, bytes));
        assertThrows(UploadFailure.class, () -> session.append(PLAYER, 1, 0, 0, bytes));
        assertThrows(UploadFailure.class, () -> session.append(PLAYER, 0, 0, 1, bytes));
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
}
