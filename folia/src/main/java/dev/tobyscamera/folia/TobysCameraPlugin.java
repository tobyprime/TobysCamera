package dev.tobyscamera.folia;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.camera.CameraItemValidator;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.net.PluginPayloadGateway;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TobysCameraPlugin extends JavaPlugin {
    private UploadCoordinator coordinator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginSettings settings = PluginSettings.from(flatten(getConfig()));
        coordinator = new UploadCoordinator(settings, new CameraItemValidator(settings.cameraTagKey()),
                this::send, (player, session) -> send(player, new Packets.UploadRejected("Photo persistence is starting")));
        PluginPayloadGateway gateway = new PluginPayloadGateway(this, coordinator);
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginPayloadGateway.CHANNEL, gateway);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginPayloadGateway.CHANNEL);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    private void send(Player player, dev.tobyscamera.common.protocol.CameraPacket packet) {
        player.sendPluginMessage(this, PluginPayloadGateway.CHANNEL, PacketCodec.encode(packet));
    }

    private static Map<String, Object> flatten(FileConfiguration config) {
        Map<String, Object> values = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) values.put(key, config.get(key));
        }
        return values;
    }
}
