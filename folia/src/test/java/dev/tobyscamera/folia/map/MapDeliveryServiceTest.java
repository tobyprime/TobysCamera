package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.PhotoPresentation;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.delivery.MapDeliveryService;
import dev.tobyscamera.folia.delivery.PendingDeliveryRepository;
import dev.tobyscamera.folia.storage.MediaTileCache;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.SqlitePhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class MapDeliveryServiceTest {
    @TempDir Path directory;

    @Test
    void reconstructsDeliveredBagMetadataFromThePersistedRecord() throws Exception {
        ItemStack bag = mock(ItemStack.class);
        MapPhotoService photos = new MapPhotoService(null, mock(PhotoRepository.class), new MediaTileCache(16_384), () -> 7);
        MapDeliveryService deliveries = new MapDeliveryService(photos, new PendingDeliveryRepository(directory));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getWorld()).thenReturn(mock(World.class));
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        PhotoRecord record = record();

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class))).thenReturn(bag);

            deliveries.deliver(player, record);

            bags.verify(() -> PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, 7,
                    record.gridWidth(), record.gridHeight(), record.metadata())));
        }

        ArgumentCaptor<ItemStack> delivered = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(delivered.capture());
        assertEquals(bag, delivered.getValue());
    }

    @Test
    void deliversQueuedPhotoFromReopenedStorageWithoutTransientMetadata() throws Exception {
        UUID playerId = UUID.randomUUID();
        PhotoRecord saved = record();
        Player queuedPlayer = mock(Player.class);
        when(queuedPlayer.getUniqueId()).thenReturn(playerId);
        try (SqlitePhotoRepository repository = new SqlitePhotoRepository(directory)) {
            repository.save(saved, Map.of(new TileCoordinate(0, 0), new byte[16_384]), new byte[16_384]);
            new MapDeliveryService(new MapPhotoService(null, repository, new MediaTileCache(16_384), () -> 7),
                    new PendingDeliveryRepository(directory)).queue(queuedPlayer, saved);
        }

        List<UUID> queuedIds = new PendingDeliveryRepository(directory).take(playerId);
        assertEquals(List.of(saved.photoId()), queuedIds);

        ItemStack bag = mock(ItemStack.class);
        Player reconnectedPlayer = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(reconnectedPlayer.getWorld()).thenReturn(mock(World.class));
        when(reconnectedPlayer.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        try (SqlitePhotoRepository reopened = new SqlitePhotoRepository(directory);
                MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            PhotoRecord restored = reopened.find(queuedIds.getFirst());
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class))).thenReturn(bag);

            new MapDeliveryService(new MapPhotoService(null, reopened, new MediaTileCache(16_384), () -> 7),
                    new PendingDeliveryRepository(directory)).deliver(reconnectedPlayer, restored);

            bags.verify(() -> PhotoBagFactory.createNegative(new PhotoBagData(restored.photoId(), PhotoBagKind.PHOTO, 7,
                    restored.gridWidth(), restored.gridHeight(), saved.metadata())));
        }
    }

    @Test
    void retainsPendingPhotoWhenRecordLookupFails() throws Exception {
        UUID playerId = UUID.randomUUID();
        PhotoRecord record = record();
        PendingDeliveryRepository pending = new PendingDeliveryRepository(directory);
        pending.add(playerId, record.photoId());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        MapDeliveryService deliveries = new MapDeliveryService(new MapPhotoService(null, mock(PhotoRepository.class),
                new MediaTileCache(16_384), () -> 7), pending);

        assertThrows(java.io.IOException.class, () -> deliveries.deliverPending(player, ignored -> {
            throw new java.io.IOException("storage unavailable");
        }));

        assertEquals(List.of(record.photoId()), pending.pending(playerId));
    }

    @Test
    void retainsPendingPhotoWhenDeliveryFails() throws Exception {
        UUID playerId = UUID.randomUUID();
        PhotoRecord record = record();
        PendingDeliveryRepository pending = new PendingDeliveryRepository(directory);
        pending.add(playerId, record.photoId());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(mock(World.class));
        MapDeliveryService deliveries = new MapDeliveryService(new MapPhotoService(null, mock(PhotoRepository.class),
                new MediaTileCache(16_384), () -> 7), pending);

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class)))
                    .thenThrow(new IllegalStateException("delivery failed"));

            assertThrows(IllegalStateException.class, () -> deliveries.deliverPending(player, ignored -> record));
        }

        assertEquals(List.of(record.photoId()), pending.pending(playerId));
    }

    @Test
    void acknowledgesPendingPhotoOnlyAfterDeliverySucceeds() throws Exception {
        UUID playerId = UUID.randomUUID();
        PhotoRecord record = record();
        PendingDeliveryRepository pending = new PendingDeliveryRepository(directory);
        pending.add(playerId, record.photoId());
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(mock(World.class));
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        MapDeliveryService deliveries = new MapDeliveryService(new MapPhotoService(null, mock(PhotoRepository.class),
                new MediaTileCache(16_384), () -> 7), pending);

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.createNegative(org.mockito.ArgumentMatchers.any(PhotoBagData.class)))
                    .thenReturn(mock(ItemStack.class));

            deliveries.deliverPending(player, ignored -> record);
        }

        assertEquals(List.of(), pending.pending(playerId));
    }

    private static PhotoRecord record() {
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world", 1, 64, -2, Instant.parse("2026-07-20T12:00:00Z"),
                new PhotoPresentation("Sunset", "At the fortress", false, true, false));
        return new PhotoRecord(UUID.randomUUID(), UUID.randomUUID(), "Toby", Instant.parse("2026-07-20T12:01:00Z"),
                1, 1, Map.of(new TileCoordinate(0, 0), 100), metadata);
    }
}
