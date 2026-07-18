package dev.tobyscamera.folia.net;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.upload.VideoUploadCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PluginPayloadGatewayTest {
    @Test
    void dispatchesDecodedPacketsThroughTheProvidedScheduler() {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        UploadCoordinator photos = mock(UploadCoordinator.class);
        VideoUploadCoordinator videos = mock(VideoUploadCoordinator.class);
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        PluginPayloadGateway gateway = new PluginPayloadGateway(plugin, scheduler, photos, videos);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        gateway.onPluginMessageReceived(PluginPayloadGateway.CHANNEL, player,
                PacketCodec.encode(new Packets.UploadBegin(1, 1)));

        verify(scheduler).runEntity(eq(player), task.capture(), any());
        task.getValue().run();
        verify(photos).handle(eq(player), any(Packets.UploadBegin.class));
    }
}
