package dev.tobyscamera.folia.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.camera.CameraFilmService;
import dev.tobyscamera.folia.config.PluginSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class VideoUploadCoordinatorTest {
    @Test
    void beginChargesEveryFinalFrameTileAndGrantsConfiguredChunkRate() {
        Player player = player();
        ItemStack camera = mock(ItemStack.class); CameraFilmService films = mock(CameraFilmService.class);
        when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(4); when(films.maximumVideoFps(camera, 10)).thenReturn(10); when(films.consume(camera, 240)).thenReturn(true);
        List<CameraPacket> sent = new ArrayList<>();
        VideoUploadCoordinator coordinator = new VideoUploadCoordinator(PluginSettings.from(Map.of()), films, (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { });

        coordinator.handle(player, new Packets.VideoBegin(3, 4, 10, 20));

        Packets.VideoGranted grant = (Packets.VideoGranted) sent.getFirst();
        assertEquals(120, grant.maxChunksPerSecond()); verify(films).consume(camera, 240);
    }

    @Test
    void rejectsFpsAboveTheHeldCamerasComponentLimitBeforeChargingFilm() {
        Player player = player();
        ItemStack camera = mock(ItemStack.class); CameraFilmService films = mock(CameraFilmService.class);
        when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(4);
        when(films.maximumVideoFps(camera, 10)).thenReturn(5);
        List<CameraPacket> sent = new ArrayList<>();
        VideoUploadCoordinator coordinator = new VideoUploadCoordinator(PluginSettings.from(Map.of()), films, (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { });

        coordinator.handle(player, new Packets.VideoBegin(1, 1, 6, 1));

        assertEquals(Packets.UploadRejected.class, sent.getFirst().getClass());
        org.mockito.Mockito.verify(films, org.mockito.Mockito.never()).consume(camera, 1);
    }

    @Test
    void rejectsSecondChunkInsideConfiguredOneChunkWindow() {
        Player player = player();
        ItemStack camera = mock(ItemStack.class); CameraFilmService films = mock(CameraFilmService.class);
        when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(1); when(films.maximumVideoFps(camera, 10)).thenReturn(10); when(films.consume(camera, 1)).thenReturn(true);
        List<CameraPacket> sent = new ArrayList<>();
        VideoUploadCoordinator coordinator = new VideoUploadCoordinator(PluginSettings.from(Map.of("video.max-upload-chunks-per-second", 1)), films, (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { });
        coordinator.handle(player, new Packets.VideoBegin(1, 1, 1, 1));
        UUID token = ((Packets.VideoGranted) sent.getFirst()).token();
        coordinator.handle(player, new Packets.VideoTileChunk(token, 0, 0, 0, 0, new byte[8_192]));
        coordinator.handle(player, new Packets.VideoTileChunk(token, 0, 0, 0, 8_192, new byte[8_192]));
        assertEquals(Packets.UploadRejected.class, sent.getLast().getClass());
    }

    @Test
    void clearsTheVideoSessionAfterChunkRateLimitIsExceeded() {
        Player player = player();
        ItemStack camera = mock(ItemStack.class); CameraFilmService films = mock(CameraFilmService.class);
        when(films.heldCamera(player)).thenReturn(camera); when(films.maximumForFilm(camera, 4)).thenReturn(1); when(films.maximumVideoFps(camera, 10)).thenReturn(10); when(films.consume(camera, 1)).thenReturn(true);
        List<CameraPacket> sent = new ArrayList<>();
        VideoUploadCoordinator coordinator = new VideoUploadCoordinator(PluginSettings.from(Map.of("video.max-upload-chunks-per-second", 1)), films, (ignored, packet) -> sent.add(packet), (ignored, session, metadata) -> { });
        coordinator.handle(player, new Packets.VideoBegin(1, 1, 1, 1));
        UUID token = ((Packets.VideoGranted) sent.getFirst()).token();
        coordinator.handle(player, new Packets.VideoTileChunk(token, 0, 0, 0, 0, new byte[8_192]));
        coordinator.handle(player, new Packets.VideoTileChunk(token, 0, 0, 0, 8_192, new byte[8_192]));

        coordinator.handle(player, new Packets.VideoFinish(token));

        verify(player).kick(org.mockito.ArgumentMatchers.any());
    }

    private static Player player() {
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getName()).thenReturn("tester");
        org.bukkit.World world = mock(org.bukkit.World.class); when(world.getKey()).thenReturn(new org.bukkit.NamespacedKey("minecraft", "world"));
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 1, 2, 3));
        return player;
    }
}
