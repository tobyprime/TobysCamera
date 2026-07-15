package dev.tobyscamera.common.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                session.append(PLAYER, x, y, 0, first);
                session.append(PLAYER, x, y, 8_192, second);
            }
        }

        assertTrue(session.isComplete());
        assertArrayEquals(second, java.util.Arrays.copyOfRange(session.tile(1, 1), 8_192, 16_384));
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

        assertThrows(UploadFailure.class, () -> new UploadSession(grant, 1, 1));
        assertThrows(UploadFailure.class, () -> new UploadSession(grant, 2, 1));
    }
}
