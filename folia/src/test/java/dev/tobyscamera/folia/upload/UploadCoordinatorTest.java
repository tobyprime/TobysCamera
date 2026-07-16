package dev.tobyscamera.folia.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.camera.CameraFilmService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class UploadCoordinatorTest {
    @Test
    void beginChargesFilmThenGrantsTokenAndRateLimitsSecondBegin() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        CameraFilmService films = mock(CameraFilmService.class);
        ItemStack camera = mock(ItemStack.class);
        when(films.heldCamera(player)).thenReturn(camera);
        when(films.maximumForFilm(camera, 4)).thenReturn(2);
        when(films.consume(camera, 4)).thenReturn(true);
        UploadCoordinator coordinator = coordinator(sent, films, (ignored, session) -> { });

        coordinator.handle(player, new Packets.UploadBegin(2, 2));
        coordinator.handle(player, new Packets.UploadBegin(1, 1));

        assertEquals(Packets.UploadGranted.class, sent.getFirst().getClass());
        assertEquals(Packets.RateLimited.class, sent.get(1).getClass());
        verify(films).consume(camera, 4);
    }

    @Test
    void kicksInvalidUploadTokenBeforeCreatingSession() {
        Player player = player();
        UploadCoordinator coordinator = coordinator(new ArrayList<>(), mock(CameraFilmService.class), (ignored, session) -> { });

        coordinator.handle(player, new Packets.UploadFinish(UUID.randomUUID()));

        verify(player).kick(any());
    }

    private static UploadCoordinator coordinator(List<CameraPacket> sent, CameraFilmService films, CompletedUploadHandler completed) {
        return new UploadCoordinator(PluginSettings.from(java.util.Map.of()), films,
                (player, packet) -> sent.add(packet), completed, ignored -> { });
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
