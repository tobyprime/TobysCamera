package dev.tobyscamera.folia;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.camera.CameraFilmService;
import dev.tobyscamera.folia.camera.CameraFilmInventoryListener;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.net.PluginPayloadGateway;
import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.map.CameraMapCopyMetadataListener;
import dev.tobyscamera.folia.delivery.MapDeliveryService;
import dev.tobyscamera.folia.delivery.MapItemDelivery;
import dev.tobyscamera.folia.bag.PhotoBagPlacementListener;
import dev.tobyscamera.folia.delivery.PendingDeliveryRepository;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.SqlitePhotoRepository;
import dev.tobyscamera.folia.storage.SqliteVideoRepository;
import dev.tobyscamera.folia.storage.VideoRepository;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.upload.VideoUploadCoordinator;
import dev.tobyscamera.folia.map.MapVideoService;
import dev.tobyscamera.folia.video.VideoPlaybackService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.HandlerList;
import net.kyori.adventure.text.Component;

public final class TobysCameraPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private UploadCoordinator coordinator;
    private VideoUploadCoordinator videoCoordinator;
    private PhotoRepository repository;
    private VideoRepository videoRepository;
    private MapPhotoService photos;
    private MapVideoService videos;
    private MapDeliveryService deliveries;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask videoPlaybackTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask uploadCleanupTask;
    private VideoPlaybackService videoPlayback;
    private PluginPayloadGateway gateway;
    private CameraFilmInventoryListener filmListener;
    private PhotoBagPlacementListener bagPlacement;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            repository = new SqlitePhotoRepository(getDataFolder().toPath());
            videoRepository = new SqliteVideoRepository(getDataFolder().toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize camera storage", exception);
        }
        photos = new MapPhotoService(this, repository);
        videos = new MapVideoService(videoRepository);
        bagPlacement = new PhotoBagPlacementListener(this, photos, videos);
        try { deliveries = new MapDeliveryService(photos, new PendingDeliveryRepository(getDataFolder().toPath())); }
        catch (IOException exception) { throw new IllegalStateException("Could not initialize pending deliveries", exception); }
        configureRuntime(PluginSettings.from(flatten(getConfig())));
        getServer().getGlobalRegionScheduler().run(this, ignored -> {
            try { photos.restore(); } catch (IOException exception) { getLogger().severe("Could not restore saved photo maps: " + exception.getMessage()); }
            try { videos.restore(); }
            catch (IOException exception) { getLogger().severe("Could not restore saved video maps: " + exception.getMessage()); }
            finally { videoPlayback.indexLoadedFrames(); }
        });
        gateway = new PluginPayloadGateway(this, coordinator, videoCoordinator);
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginPayloadGateway.CHANNEL, gateway);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginPayloadGateway.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CameraMapCopyMetadataListener(), this);
        getServer().getPluginManager().registerEvents(bagPlacement, this);
        getCommand("tobyscamera").setExecutor(this);
    }

    private void configureRuntime(PluginSettings settings) {
        if (videoPlaybackTask != null) videoPlaybackTask.cancel();
        if (uploadCleanupTask != null) uploadCleanupTask.cancel();
        if (videoPlayback != null) HandlerList.unregisterAll(videoPlayback);
        if (filmListener != null) HandlerList.unregisterAll(filmListener);
        CameraFilmService films = new CameraFilmService(settings.cameraTagKey(), settings.filmTagKey(), settings.maxGridSize());
        coordinator = new UploadCoordinator(settings, films, this::send,
                (player, session, metadata) -> createAndDeliver(player, session, metadata),
                player -> player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.3f));
        videoCoordinator = new VideoUploadCoordinator(settings, films, this::send, this::createAndDeliverVideo, coordinator::discardCaptureIntent);
        if (gateway != null) gateway.setCoordinators(coordinator, videoCoordinator);
        filmListener = new CameraFilmInventoryListener(films);
        getServer().getPluginManager().registerEvents(filmListener, this);
        videoPlayback = new VideoPlaybackService(this, videos, settings.videoMaxActiveMapFrames(), settings.videoMaxUpdateDistance());
        bagPlacement.setFrameRefresher(videoPlayback::refreshFrame);
        videoPlayback.indexLoadedFrames();
        getServer().getPluginManager().registerEvents(videoPlayback, this);
        videoPlaybackTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, ignored -> videoPlayback.tick(), 1L, 1L);
        uploadCleanupTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, ignored -> {
            var now = java.time.Instant.now();
            coordinator.expireSessions(now);
            videoCoordinator.expireSessions(now);
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (videoPlaybackTask != null) videoPlaybackTask.cancel();
        if (uploadCleanupTask != null) uploadCleanupTask.cancel();
        if (repository != null) try { repository.close(); } catch (IOException exception) { getLogger().warning("Could not close photo storage: " + exception.getMessage()); }
        if (videoRepository != null) try { videoRepository.close(); } catch (IOException exception) { getLogger().warning("Could not close video storage: " + exception.getMessage()); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        if (!sender.hasPermission("tobyscamera.reload")) { sender.sendMessage(Component.text("You do not have permission to reload TobysCamera.")); return true; }
        try {
            reloadConfig();
            configureRuntime(PluginSettings.from(flatten(getConfig())));
            sender.sendMessage(Component.text("TobysCamera configuration reloaded."));
        } catch (RuntimeException exception) {
            getLogger().warning("Could not reload TobysCamera configuration: " + exception.getMessage());
            sender.sendMessage(Component.text("TobysCamera configuration reload failed; keeping the previous configuration."));
        }
        return true;
    }

    private void createAndDeliverVideo(Player player, dev.tobyscamera.common.upload.VideoUploadSession session, dev.tobyscamera.folia.upload.PhotoMetadata metadata) {
        var world = player.getWorld();
        getServer().getGlobalRegionScheduler().run(this, ignored -> {
            try {
                var record = videos.createMaps(player.getUniqueId(), world, session);
                getServer().getAsyncScheduler().runNow(this, task -> {
                    try {
                        videos.persist(record, session);
                        getServer().getGlobalRegionScheduler().run(this, ignored2 -> {
                            var bag = videos.bag(world, record, session, metadata);
                            player.getScheduler().run(this, task2 -> {
                                try {
                                    MapItemDelivery.deliver(java.util.List.of(bag), player.getInventory()::addItem,
                                            item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                                    getLogger().info("Delivered a video bag for " + record.videoId() + " to " + player.getName() + ".");
                                    send(player, new Packets.VideoCreated(record.videoId(), record.mapIds().values().stream().toList(), record.gridWidth(), record.gridHeight(), record.fps(), record.frameCount()));
                                } catch (RuntimeException exception) {
                                    getLogger().warning("Could not deliver video maps to " + player.getName() + ": " + exception.getMessage());
                                    send(player, new Packets.UploadRejected("Could not deliver video maps"));
                                }
                            }, () -> { });
                        });
                    } catch (IOException exception) {
                        getServer().getGlobalRegionScheduler().run(this, ignored2 -> videos.discard(record));
                        player.getScheduler().run(this, task2 -> send(player, new Packets.UploadRejected("Could not save video maps")), () -> { });
                    }
                });
            } catch (RuntimeException exception) {
                player.getScheduler().run(this, task -> send(player, new Packets.UploadRejected("Could not create video maps")), () -> { });
            }
        });
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

    private void createAndDeliver(Player player, dev.tobyscamera.common.upload.UploadSession session,
            dev.tobyscamera.folia.upload.PhotoMetadata metadata) {
        var world = player.getWorld();
        getServer().getGlobalRegionScheduler().run(this, ignored -> {
            try {
                var record = photos.createMaps(player.getUniqueId(), world, session);
                getServer().getAsyncScheduler().runNow(this, asyncTask -> {
                    try {
                        photos.persist(record, session);
                        getServer().getGlobalRegionScheduler().run(this, ignored2 -> {
                            var bag = photos.bag(world, record, session, metadata);
                            player.getScheduler().run(this, task -> {
                                MapItemDelivery.deliver(java.util.List.of(bag), player.getInventory()::addItem,
                                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                                send(player, new Packets.PhotoCreated(record.photoId(), record.mapIds().values().stream().toList(), record.gridWidth(), record.gridHeight()));
                            }, () -> { try { deliveries.queue(player, record, metadata); } catch (IOException exception) { getLogger().warning("Could not queue photo delivery: " + exception.getMessage()); } });
                        });
                    } catch (IOException exception) {
                        getServer().getGlobalRegionScheduler().run(this, ignored2 -> photos.discard(record));
                        player.getScheduler().run(this, task -> send(player, new Packets.UploadRejected("Could not save photo maps")), () -> { });
                    }
                });
            } catch (RuntimeException exception) {
                player.getScheduler().run(this, task -> send(player, new Packets.UploadRejected("Could not create photo maps")), () -> { });
                getLogger().warning("Could not create photo map: " + exception.getMessage());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().run(this, task -> {
            try {
                for (var photoId : new PendingDeliveryRepository(getDataFolder().toPath()).take(player.getUniqueId())) {
                    var record = repository.find(photoId);
                    if (record != null) deliveries.deliver(player, record);
                }
            } catch (IOException exception) { getLogger().warning("Could not deliver queued photos: " + exception.getMessage()); }
        }, () -> { });
    }
}
