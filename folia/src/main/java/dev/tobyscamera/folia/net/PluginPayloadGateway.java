package dev.tobyscamera.folia.net;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.ProtocolException;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PluginPayloadGateway implements PluginMessageListener {
    public static final String CHANNEL = "tobyscamera:main";
    private static final int MAX_PAYLOAD_BYTES = PacketCodec.MAX_CHUNK_BYTES + 64;
    private final Plugin plugin;
    private final UploadCoordinator coordinator;

    public PluginPayloadGateway(Plugin plugin, UploadCoordinator coordinator) {
        this.plugin = plugin;
        this.coordinator = coordinator;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length > MAX_PAYLOAD_BYTES) return;
        final CameraPacket packet;
        try {
            packet = PacketCodec.decode(message);
        } catch (ProtocolException exception) {
            return;
        }
        player.getScheduler().run(plugin, ignored -> coordinator.handle(player, packet), () -> { });
    }

    public void send(Player player, CameraPacket packet) {
        player.sendPluginMessage(plugin, CHANNEL, PacketCodec.encode(packet));
    }
}
