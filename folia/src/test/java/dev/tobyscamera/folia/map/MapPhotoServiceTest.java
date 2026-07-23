package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.PhotoPresentation;
import dev.tobyscamera.common.upload.UploadGrant;
import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.storage.MediaTileCache;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MapPhotoServiceTest {
    @Test
    void createsRecordsWithTheCapturedOwnerNameAndMetadata() {
        MapPhotoService photos = new MapPhotoService(null, mock(PhotoRepository.class), new MediaTileCache(16_384), () -> 7);
        UUID ownerId = UUID.randomUUID();
        UploadSession session = new UploadSession(new UploadGrant(UUID.randomUUID(), ownerId, Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1), 1, 1);
        PhotoMetadata metadata = record().metadata();

        PhotoRecord record = photos.createMaps(ownerId, "Toby", null, session, metadata);

        org.junit.jupiter.api.Assertions.assertEquals("Toby", record.ownerName());
        org.junit.jupiter.api.Assertions.assertEquals(metadata, record.metadata());
    }

    @Test
    void originalBagRestoresPersistedMetadataAndRemainsNegative() {
        ItemStack negative = mock(ItemStack.class);
        MapPhotoService photos = new MapPhotoService(null, mock(PhotoRepository.class), new MediaTileCache(16_384), () -> 7);
        PhotoRecord record = record();

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class))).thenReturn(negative);

            var bag = photos.originalBag(null, record);

            assertSame(negative, bag);
            bags.verify(() -> PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, 7,
                    record.gridWidth(), record.gridHeight(), record.metadata())));
        }
    }

    @Test
    void adminBagIsPrintableCopyWithoutNegativeLoreAndWithPersistedMetadata() {
        ItemStack negative = mock(ItemStack.class);
        ItemStack printableCopy = mock(ItemStack.class);
        MapPhotoService photos = new MapPhotoService(null, mock(PhotoRepository.class), new MediaTileCache(16_384), () -> 7);
        PhotoRecord record = record();

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class))).thenReturn(negative);
            bags.when(() -> PhotoBagFactory.copyForPrint(negative)).thenReturn(printableCopy);

            var bag = photos.adminBag(null, record);

            assertSame(printableCopy, bag);
            bags.verify(() -> PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, 7,
                    record.gridWidth(), record.gridHeight(), record.metadata())));
            bags.verify(() -> PhotoBagFactory.copyForPrint(negative));
        }
    }

    @Test
    void deleteDelegatesAndInvalidatesCachedPhotoPreviewAndTiles() throws Exception {
        PhotoRepository repository = mock(PhotoRepository.class);
        MediaTileCache cache = new MediaTileCache(32_768);
        MapPhotoService photos = new MapPhotoService(null, repository, cache, () -> 7);
        PhotoRecord record = record();
        byte[] pixels = new byte[16_384];
        when(repository.readPreview(record.photoId())).thenReturn(pixels);
        when(repository.readTile(record.photoId(), new TileCoordinate(0, 0))).thenReturn(pixels);

        photos.previewPixels(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, 7, 1, 1, record.metadata()));
        photos.tilePixels(new MediaMapDescriptor.PhotoTile(100, record.photoId(), new TileCoordinate(0, 0)));
        photos.delete(record.photoId());

        verify(repository).delete(record.photoId());
        assertNull(cache.find(MediaTileCache.Key.photoPreview(record.photoId())));
        assertNull(cache.find(MediaTileCache.Key.photoTile(record.photoId(), new TileCoordinate(0, 0))));
    }

    private static PhotoRecord record() {
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world", 1, 64, -2, Instant.parse("2026-07-20T12:00:00Z"),
                new PhotoPresentation("Sunset", "At the fortress", false, true, false));
        return new PhotoRecord(UUID.randomUUID(), UUID.randomUUID(), "Toby", Instant.parse("2026-07-20T12:01:00Z"),
                1, 1, Map.of(new TileCoordinate(0, 0), 100), metadata);
    }
}
