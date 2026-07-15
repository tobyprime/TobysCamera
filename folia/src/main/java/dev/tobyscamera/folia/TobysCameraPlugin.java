package dev.tobyscamera.folia;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.camera.CameraItemValidator;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.net.PluginPayloadGateway;
import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.SqlitePhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TobysCameraPlugin extends JavaPlugin {
    private UploadCoordinator coordinator;
    private PhotoRepository repository;
    private MapPhotoService photos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginSettings settings = PluginSettings.from(flatten(getConfig()));
        try {
            repository = new SqlitePhotoRepository(getDataFolder().toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize photo storage", exception);
        }
        photos = new MapPhotoService(this, repository);
        getServer().getGlobalRegionScheduler().run(this, ignored -> {
            try { photos.restore(); } catch (IOException exception) { getLogger().severe("Could not restore saved photo maps: " + exception.getMessage()); }
        });
        coordinator = new UploadCoordinator(settings, new CameraItemValidator(settings.cameraTagKey()), this::send,
                (player, session) -> createAndDeliver(player, session));
        PluginPayloadGateway gateway = new PluginPayloadGateway(this, coordinator);
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginPayloadGateway.CHANNEL, gateway);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginPayloadGateway.CHANNEL);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (repository != null) try { repository.close(); } catch (IOException exception) { getLogger().warning("Could not close photo storage: " + exception.getMessage()); }
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

    private void createAndDeliver(Player player, dev.tobyscamera.common.upload.UploadSession session) {
        var world = player.getWorld();
        getServer().getGlobalRegionScheduler().run(this, ignored -> {
            try {
                var record = photos.create(player.getUniqueId(), world, session);
                player.getScheduler().run(this, task -> {
                    for (var coordinate : record.mapIds().keySet()) {
                        var leftovers = player.getInventory().addItem(photos.mapItem(record, coordinate));
                        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    }
                    send(player, new Packets.PhotoCreated(record.photoId(), record.mapIds().values().stream().toList(), record.gridWidth(), record.gridHeight()));
                }, () -> { });
            } catch (IOException | RuntimeException exception) {
                player.getScheduler().run(this, task -> send(player, new Packets.UploadRejected("Could not create photo maps")), () -> { });
                getLogger().warning("Could not create photo map: " + exception.getMessage());
            }
        });
    }
}
