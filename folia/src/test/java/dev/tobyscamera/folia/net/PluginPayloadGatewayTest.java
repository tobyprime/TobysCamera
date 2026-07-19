package dev.tobyscamera.folia.net;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PluginPayloadGatewayTest {
    @Test
    void dispatchesDecodedPhotoPacketsThroughTheProvidedScheduler() {
        Plugin plugin = plugin();
        Player player = mock(Player.class);
        UploadCoordinator photos = mock(UploadCoordinator.class);
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        PluginPayloadGateway gateway = new PluginPayloadGateway(plugin, scheduler, photos);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        gateway.onPluginMessageReceived(PluginPayloadGateway.CHANNEL, player,
                PacketCodec.encode(new Packets.UploadBegin(1, 1)));

        verify(scheduler).runEntity(eq(player), task.capture(), any());
        task.getValue().run();
        verify(photos).handle(eq(player), any(Packets.UploadBegin.class));
    }

    @Test
    void rejectsRemovedPacketIdentifiersToAvoidLeavingOlderClientsWaiting() {
        Plugin plugin = plugin();
        Player player = mock(Player.class);
        PluginPayloadGateway gateway = new PluginPayloadGateway(plugin, mock(ServerTaskScheduler.class), mock(UploadCoordinator.class));
        ArgumentCaptor<byte[]> reply = ArgumentCaptor.forClass(byte[].class);

        gateway.onPluginMessageReceived(PluginPayloadGateway.CHANNEL, player, new byte[] {PacketCodec.VERSION, 9});

        verify(player).sendPluginMessage(eq(plugin), eq(PluginPayloadGateway.CHANNEL), reply.capture());
        assertInstanceOf(Packets.UploadRejected.class, PacketCodec.decode(reply.getValue()));
    }

    private static Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }
}
