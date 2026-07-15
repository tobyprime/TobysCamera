package dev.tobyscamera.folia.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.config.PluginSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class UploadCoordinatorTest {
    @Test
    void grantsTaggedCameraAndRateLimitsSecondCapture() {
        Player player = player();
        List<CameraPacket> sent = new ArrayList<>();
        UploadCoordinator coordinator = coordinator(sent, (ignored, session) -> { });

        coordinator.handle(player, new Packets.CaptureIntent());
        coordinator.handle(player, new Packets.CaptureIntent());

        assertEquals(Packets.UploadGranted.class, sent.getFirst().getClass());
        assertEquals(Packets.RateLimited.class, sent.get(1).getClass());
    }

    @Test
    void kicksInvalidUploadTokenBeforeCreatingSession() {
        Player player = player();
        UploadCoordinator coordinator = coordinator(new ArrayList<>(), (ignored, session) -> { });

        coordinator.handle(player, new Packets.UploadFinish(UUID.randomUUID()));

        verify(player).kick(any());
    }

    private static UploadCoordinator coordinator(List<CameraPacket> sent, CompletedUploadHandler completed) {
        return new UploadCoordinator(PluginSettings.from(java.util.Map.of()), ignored -> true,
                (player, packet) -> sent.add(packet), completed, ignored -> { });
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
