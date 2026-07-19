package dev.tobyscamera.folia.net;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.common.protocol.ProtocolException;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PluginPayloadGateway implements PluginMessageListener {
    public static final String CHANNEL = "tobyscamera:main";
    private static final int MAX_PAYLOAD_BYTES = PacketCodec.MAX_CHUNK_BYTES + 64;
    private final Plugin plugin;
    private final ServerTaskScheduler scheduler;
    private volatile UploadCoordinator coordinator;

    public PluginPayloadGateway(Plugin plugin, ServerTaskScheduler scheduler, UploadCoordinator coordinator) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.coordinator = coordinator;
    }

    public void setCoordinator(UploadCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        if (message.length > MAX_PAYLOAD_BYTES) {
            plugin.getLogger().warning("Rejected oversized camera payload from " + player.getName() + ": " + message.length + " bytes.");
            return;
        }
        final CameraPacket packet;
        try {
            packet = PacketCodec.decode(message);
        } catch (ProtocolException exception) {
            plugin.getLogger().warning("Rejected malformed camera payload from " + player.getName() + ": " + exception.getMessage());
            send(player, new Packets.UploadRejected("Unsupported camera client packet"));
            return;
        }
        scheduler.runEntity(player, () -> new CameraPacketRouter(
                photoPacket -> coordinator.handle(player, photoPacket)).route(packet), () -> { });
    }

    public void send(Player player, CameraPacket packet) {
        player.sendPluginMessage(plugin, CHANNEL, PacketCodec.encode(packet));
    }
}
