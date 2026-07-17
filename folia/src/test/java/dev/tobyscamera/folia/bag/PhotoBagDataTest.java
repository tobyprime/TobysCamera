package dev.tobyscamera.folia.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoBagDataTest {
    @Test
    void retainsTheReferencedMediaAndPreviewDimensions() {
        UUID mediaId = UUID.randomUUID();
        PhotoBagData bag = new PhotoBagData(mediaId, PhotoBagKind.VIDEO, 42, 12, 8);

        assertEquals(mediaId, bag.mediaId());
        assertEquals(PhotoBagKind.VIDEO, bag.kind());
        assertEquals(42, bag.previewMapId());
        assertEquals(12, bag.gridWidth());
        assertEquals(8, bag.gridHeight());
    }

    @Test
    void rejectsAnInvalidPreviewOrGrid() {
        assertThrows(IllegalArgumentException.class, () -> new PhotoBagData(UUID.randomUUID(), PhotoBagKind.PHOTO, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PhotoBagData(UUID.randomUUID(), PhotoBagKind.PHOTO, 1, 0, 1));
    }
}
