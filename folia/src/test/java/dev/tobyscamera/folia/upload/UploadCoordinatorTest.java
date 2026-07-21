package dev.tobyscamera.folia.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.camera.CameraFilmService;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class UploadCoordinatorTest {
    @Test
    void reportsActiveUploadsTheirDeclaredTilesAndReservedBytes() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(2);
        when(films.consume(camera, 4)).thenReturn(true);
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });
        coordinator.handle(player, new Packets.UploadBegin(2, 2));
        assertEquals(new UploadCoordinator.Status(1, 4, 92_160, 16_777_216), coordinator.status());
    }

    @Test
    void expiresIncompleteUploadSessionsWithoutWaitingForAnotherPacket() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.consume(camera, 1)).thenReturn(true);
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });

        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(1, coordinator.expireSessions(Instant.now().plusSeconds(61)));
    }

    @Test
    void beginChargesFilmThenGrantsTokenAndRateLimitsSecondBegin() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(3);
        when(films.consume(camera, 9)).thenReturn(true);
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, coordinates) -> { });

        coordinator.handle(player, new Packets.UploadBegin(3, 3));
        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(Packets.UploadGranted.class, sent.getFirst().getClass());
        assertEquals(Packets.RateLimited.class, sent.get(1).getClass());
        verify(films).consume(camera, 9);
    }

    @Test
    void beginConsumesMagicPhotoCameraThenGrantsToken() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.isMagicPhoto(camera)).thenReturn(true);
        when(films.consumeMagicPhoto(camera)).thenReturn(true);
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });

        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(Packets.UploadGranted.class, sent.getFirst().getClass());
        verify(films).consumeMagicPhoto(camera);
        org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
    }

    @Test
    void rejectedMagicPhotoUploadDoesNotConsumeCamera() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.isMagicPhoto(camera)).thenReturn(true);
        UploadCoordinator coordinator = new UploadCoordinator(PluginSettings.from(java.util.Map.of("upload.max-active-upload-bytes", 16_384L)), films,
                (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { }, ignored -> { });

        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
        org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consumeMagicPhoto(camera);
        org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
    }

    @Test
    void kicksInvalidUploadTokenBeforeCreatingSession() {
        Player player = player();
        UploadCoordinator coordinator = coordinator(new ArrayList<>(), mock(CameraFilmService.class), (ignored, session, coordinates) -> { });

        coordinator.handle(player, new Packets.UploadFinish(UUID.randomUUID()));

        verify(player).kick(any());
    }

    @Test
    void rejectsCaptureWhenPlayerLacksUploadPermission() {
        Player player = player();
        when(player.hasPermission("tobyscamera.upload")).thenReturn(false);
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        when(films.heldCamera(player)).thenReturn(mock(ItemStack.class));
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> { });

        coordinator.handle(player, new Packets.CaptureIntent());

        assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
    }

    @Test
    void acceptsClientPreviewBeforeCompletingThePhoto() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.consume(camera, 1)).thenReturn(true);
        AtomicReference<dev.tobyscamera.common.upload.UploadSession> completed = new AtomicReference<>();
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session, metadata) -> completed.set(session));

        coordinator.handle(player, new Packets.UploadBegin(1, 1));
        UUID token = ((Packets.UploadGranted) sent.getFirst()).token();
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 0, filled((byte) 17, 8_192)));
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 8_192, filled((byte) 23, 8_192)));
        coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 0, new byte[8_192]));
        coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 8_192, new byte[8_192]));
        coordinator.handle(player, new Packets.UploadFinish(token));

        assertEquals(23, Byte.toUnsignedInt(completed.get().previewPixels()[8_192]));
    }

    @Test
    void completesWhenFinishArrivesBeforeOutOfOrderMediaChunks() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.consume(camera, 1)).thenReturn(true);
        AtomicReference<dev.tobyscamera.common.upload.UploadSession> completed = new AtomicReference<>();
        UploadCoordinator coordinator = coordinator(sent, films,
                (ignored, session, metadata) -> completed.set(session));

        coordinator.handle(player, new Packets.UploadBegin(1, 1));
        UUID token = ((Packets.UploadGranted) sent.getFirst()).token();
        byte[] first = filled((byte) 31, 8_192);
        byte[] second = filled((byte) 47, 8_192);
        coordinator.handle(player, new Packets.UploadFinish(token));
        coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 8_192, second));
        coordinator.handle(player, new Packets.UploadTileChunk(token, 0, 0, 0, first));
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 8_192, second));
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 0, first));

        assertNotNull(completed.get());
        assertTrue(completed.get().isComplete());
        assertEquals(1, sent.size());
        verify(player, never()).kick(any());
    }

    @Test
    void rejectsPhotoSecondChunkInsideConfiguredOneChunkWindow() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        when(films.consume(camera, 1)).thenReturn(true);
        UploadCoordinator coordinator = new UploadCoordinator(PluginSettings.from(java.util.Map.of("upload.max-chunks-per-second", 1)), films,
                (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { }, ignored -> { });

        coordinator.handle(player, new Packets.UploadBegin(1, 1));
        UUID token = ((Packets.UploadGranted) sent.getFirst()).token();
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 0, new byte[8_192]));
        coordinator.handle(player, new Packets.UploadPreviewChunk(token, 8_192, new byte[8_192]));

        assertEquals(Packets.UploadRejected.class, sent.getLast().getClass());
    }

    @Test
    void rejectsPhotoThatExceedsTheConfiguredActiveUploadMemoryBudget() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(1);
        UploadCoordinator coordinator = new UploadCoordinator(PluginSettings.from(java.util.Map.of("upload.max-active-upload-bytes", 16_384L)), films,
                (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { }, ignored -> { });

        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
        org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
    }

    private static UploadCoordinator coordinator(List<CameraPacket> sent, CameraFilmService films, CompletedUploadHandler completed) {
        return new UploadCoordinator(PluginSettings.from(java.util.Map.of()), films,
                (player, packet) -> sent.add(packet), completed, ignored -> { });
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.hasPermission("tobyscamera.upload")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getKey()).thenReturn(new org.bukkit.NamespacedKey("minecraft", "world"));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 1, 2, 3));
        return player;
    }

    private static byte[] filled(byte value, int length) {
        byte[] result = new byte[length]; java.util.Arrays.fill(result, value); return result;
    }
}
