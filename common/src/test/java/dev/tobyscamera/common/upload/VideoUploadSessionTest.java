package dev.tobyscamera.common.upload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoUploadSessionTest {
    @Test
    void becomesCompleteOnlyAfterEveryFrameTileIsFilled() {
        UUID player = UUID.randomUUID();
        VideoUploadSession session = new VideoUploadSession(new UploadGrant(UUID.randomUUID(), player, Instant.EPOCH, Instant.ofEpochSecond(60), 2), 2, 1, 10, 2);
        fillPreview(session, player);
        for (int frame = 0; frame < 2; frame++) for (int x = 0; x < 2; x++) session.append(player, frame, x, 0, 0, new byte[16_384]);
        assertTrue(session.isComplete());
    }

    @Test
    void remainsIncompleteWhenOneFrameTileIsMissing() {
        UUID player = UUID.randomUUID();
        VideoUploadSession session = new VideoUploadSession(new UploadGrant(UUID.randomUUID(), player, Instant.EPOCH, Instant.ofEpochSecond(60), 1), 1, 1, 10, 2);
        fillPreview(session, player);
        session.append(player, 0, 0, 0, 0, new byte[16_384]);
        assertFalse(session.isComplete());
    }

    @Test
    void rejectsMediaUntilItsContiguousPreviewIsComplete() {
        UUID player = UUID.randomUUID();
        VideoUploadSession session = new VideoUploadSession(new UploadGrant(UUID.randomUUID(), player, Instant.EPOCH, Instant.ofEpochSecond(60), 1), 1, 1, 10, 1);
        byte[] chunk = new byte[8_192];

        assertThrows(UploadFailure.class, () -> session.append(player, 0, 0, 0, 0, chunk));
        assertFalse(session.isComplete());
        assertThrows(UploadFailure.class, session::previewPixels);
        session.appendPreview(player, 0, chunk);
        assertFalse(session.previewComplete());
        session.appendPreview(player, 8_192, chunk);
        assertTrue(session.previewComplete());
        session.append(player, 0, 0, 0, 0, new byte[16_384]);
        assertTrue(session.isComplete());
    }

    private static void fillPreview(VideoUploadSession session, UUID player) {
        session.appendPreview(player, 0, new byte[8_192]);
        session.appendPreview(player, 8_192, new byte[8_192]);
    }
}
