package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.storage.VideoRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BagPreviewRestoreTest {
    @Test
    void rejectsLegacyPhotoWithoutPersistedPreview() throws Exception {
        PhotoRepository repository = Mockito.mock(PhotoRepository.class);
        UUID id = UUID.randomUUID();

        assertThrows(java.io.IOException.class, () -> new MapPhotoService(null, repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.PHOTO, 72, 1, 1)));
        Mockito.verify(repository, Mockito.never()).readTile(Mockito.any(), Mockito.any());
    }

    @Test
    void readsTheClientSuppliedPhotoPreviewBeforeConsideringSourceTiles() throws Exception {
        PhotoRepository repository = Mockito.mock(PhotoRepository.class);
        UUID id = UUID.randomUUID();
        byte[] preview = filled((byte) 73);
        when(repository.readPreview(id)).thenReturn(preview);

        assertArrayEquals(preview, new MapPhotoService(null, repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.PHOTO, 72, 1, 1)));
    }

    @Test
    void rejectsLegacyVideoWithoutPersistedPreview() throws Exception {
        VideoRepository repository = Mockito.mock(VideoRepository.class);
        UUID id = UUID.randomUUID();

        assertThrows(java.io.IOException.class, () -> new MapVideoService(repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.VIDEO, 73, 1, 1)));
        Mockito.verify(repository, Mockito.never()).readTile(Mockito.any(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    void readsTheClientSuppliedVideoPreviewBeforeConsideringSourceTiles() throws Exception {
        VideoRepository repository = Mockito.mock(VideoRepository.class);
        UUID id = UUID.randomUUID();
        byte[] preview = filled((byte) 84);
        when(repository.readPreview(id)).thenReturn(preview);

        assertArrayEquals(preview, new MapVideoService(repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.VIDEO, 73, 1, 1)));
    }

    private static byte[] filled(byte value) {
        byte[] pixels = new byte[16_384];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
