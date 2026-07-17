package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void rebuildsPhotoPreviewPixelsFromPersistedTiles() throws Exception {
        PhotoRepository repository = Mockito.mock(PhotoRepository.class);
        UUID id = UUID.randomUUID();
        byte[] pixels = filled((byte) 38);
        when(repository.readTile(id, new TileCoordinate(0, 0))).thenReturn(pixels);

        assertArrayEquals(pixels, new MapPhotoService(null, repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.PHOTO, 72, 1, 1)));
    }

    @Test
    void rebuildsVideoPreviewPixelsFromFirstPersistedFrame() throws Exception {
        VideoRepository repository = Mockito.mock(VideoRepository.class);
        UUID id = UUID.randomUUID();
        byte[] pixels = filled((byte) 65);
        when(repository.readTile(id, 0, new TileCoordinate(0, 0))).thenReturn(pixels);

        assertArrayEquals(pixels, new MapVideoService(repository)
                .previewPixels(new PhotoBagData(id, PhotoBagKind.VIDEO, 73, 1, 1)));
    }

    private static byte[] filled(byte value) {
        byte[] pixels = new byte[16_384];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
