package dev.tobyscamera.folia.net;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.ProtocolException;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.upload.VideoUploadCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PluginPayloadGateway implements PluginMessageListener {
    public static final String CHANNEL = "tobyscamera:main";
    private static final int MAX_PAYLOAD_BYTES = PacketCodec.MAX_CHUNK_BYTES + 64;
    private final Plugin plugin;
    private final UploadCoordinator coordinator;
    private final VideoUploadCoordinator videoCoordinator;

    public PluginPayloadGateway(Plugin plugin, UploadCoordinator coordinator, VideoUploadCoordinator videoCoordinator) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.videoCoordinator = videoCoordinator;
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
            return;
        }
        plugin.getLogger().info("Received camera packet " + packet.getClass().getSimpleName() + " from " + player.getName() + ".");
        player.getScheduler().run(plugin, ignored -> new CameraPacketRouter(
                photoPacket -> coordinator.handle(player, photoPacket),
                videoPacket -> videoCoordinator.handle(player, videoPacket)).route(packet), () -> { });
    }

    public void send(Player player, CameraPacket packet) {
        plugin.getLogger().info("Sending camera packet " + packet.getClass().getSimpleName() + " to " + player.getName() + ".");
        player.sendPluginMessage(plugin, CHANNEL, PacketCodec.encode(packet));
    }
}
