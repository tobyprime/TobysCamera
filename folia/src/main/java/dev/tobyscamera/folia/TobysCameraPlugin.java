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
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.status.PluginRuntimeStatus;
import dev.tobyscamera.folia.map.MediaMapActivationListener;
import dev.tobyscamera.folia.map.VirtualMapDeliveryScheduler;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import dev.tobyscamera.folia.scheduler.ServerTaskSchedulers;
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
    private PhotoRepository repository;
    private MapPhotoService photos;
    private MapDeliveryService deliveries;
    private ServerTaskScheduler scheduler;
    private ServerTaskScheduler.TaskHandle uploadCleanupTask;
    private ServerTaskScheduler.TaskHandle deliveryTickTask;
    private PluginPayloadGateway gateway;
    private CameraFilmInventoryListener filmListener;
    private PhotoBagPlacementListener bagPlacement;
    private MediaMapActivationListener mediaActivation;
    private PluginRuntimeStatus runtimeStatus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        scheduler = ServerTaskSchedulers.create(this);
        try {
            repository = new SqlitePhotoRepository(getDataFolder().toPath());
            runtimeStatus = new PluginRuntimeStatus(repository.stats());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize camera storage", exception);
        }
        photos = new MapPhotoService(this, repository);
        bagPlacement = new PhotoBagPlacementListener(this, photos, scheduler);
        try { deliveries = new MapDeliveryService(photos, new PendingDeliveryRepository(getDataFolder().toPath())); }
        catch (IOException exception) { throw new IllegalStateException("Could not initialize pending deliveries", exception); }
        configureRuntime(PluginSettings.from(flatten(getConfig())));
        mediaActivation = new MediaMapActivationListener(this, scheduler, photos);
        mediaActivation.setDeliveryLimits(deliveryLimits(PluginSettings.from(flatten(getConfig()))));
        deliveryTickTask = scheduler.runGlobalRepeating(1L, 1L, mediaActivation::tickDelivery);
        bagPlacement.setFrameRefresher(mediaActivation::refreshFrame);
        bagPlacement.setHeldMapRefresher(mediaActivation::refreshHeldMaps);
        getServer().getPluginManager().registerEvents(mediaActivation, this);
        getServer().getOnlinePlayers().forEach(mediaActivation::refreshVisibleFrames);
        gateway = new PluginPayloadGateway(this, scheduler, coordinator);
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginPayloadGateway.CHANNEL, gateway);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginPayloadGateway.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CameraMapCopyMetadataListener(), this);
        getServer().getPluginManager().registerEvents(bagPlacement, this);
        getCommand("tobyscamera").setExecutor(this);
    }

    private void configureRuntime(PluginSettings settings) {
        if (uploadCleanupTask != null) uploadCleanupTask.cancel();
        if (filmListener != null) HandlerList.unregisterAll(filmListener);
        CameraFilmService films = new CameraFilmService(settings.cameraTagKey(), settings.filmTagKey(), settings.maxGridSize());
        coordinator = new UploadCoordinator(settings, films, this::send,
                (player, session, metadata) -> createAndDeliver(player, session, metadata),
                player -> player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.3f));
        if (gateway != null) gateway.setCoordinator(coordinator);
        filmListener = new CameraFilmInventoryListener(films);
        getServer().getPluginManager().registerEvents(filmListener, this);
        uploadCleanupTask = scheduler.runGlobalRepeating(20L, 20L, () -> {
            var now = java.time.Instant.now();
            coordinator.expireSessions(now);
        });
        if (mediaActivation != null) mediaActivation.setDeliveryLimits(deliveryLimits(settings));
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (uploadCleanupTask != null) uploadCleanupTask.cancel();
        if (deliveryTickTask != null) deliveryTickTask.cancel();
        if (mediaActivation != null) mediaActivation.clear();
        if (repository != null) try { repository.close(); } catch (IOException exception) { getLogger().warning("Could not close photo storage: " + exception.getMessage()); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        if (args[0].equalsIgnoreCase("status")) return status(sender);
        if (!args[0].equalsIgnoreCase("reload")) return false;
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

    private static VirtualMapDeliveryScheduler.Limits deliveryLimits(PluginSettings settings) {
        return new VirtualMapDeliveryScheduler.Limits(settings.virtualMapMaxConcurrentReads(),
                settings.virtualMapPerPlayerMapsPerTick(), settings.virtualMapPerPlayerBytesPerTick(),
                settings.virtualMapGlobalBytesPerTick());
    }

    private void createAndDeliver(Player player, dev.tobyscamera.common.upload.UploadSession session,
            dev.tobyscamera.folia.upload.PhotoMetadata metadata) {
        var world = player.getWorld();
        scheduler.runGlobal(() -> {
            try {
                var record = photos.createMaps(player.getUniqueId(), world, session);
                scheduler.runAsync(() -> {
                    try {
                        photos.persist(record, session);
                        runtimeStatus.recordPersisted(record.mapIds().size());
                        scheduler.runGlobal(() -> {
                            var bag = photos.bag(world, record, session, metadata);
                            scheduler.runEntity(player, () -> {
                                MapItemDelivery.deliver(java.util.List.of(bag), player.getInventory()::addItem,
                                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                                send(player, new Packets.PhotoCreated(record.photoId(), record.mapIds().values().stream().toList(), record.gridWidth(), record.gridHeight()));
                            }, () -> { try { deliveries.queue(player, record, metadata); } catch (IOException exception) { getLogger().warning("Could not queue photo delivery: " + exception.getMessage()); } });
                        });
                    } catch (IOException exception) {
                        scheduler.runGlobal(() -> photos.discard(record));
                        scheduler.runEntity(player, () -> send(player, new Packets.UploadRejected("Could not save photo maps")), () -> { });
                    }
                });
            } catch (RuntimeException exception) {
                scheduler.runEntity(player, () -> send(player, new Packets.UploadRejected("Could not create photo maps")), () -> { });
                getLogger().warning("Could not create photo map: " + exception.getMessage());
            }
        });
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("tobyscamera.status")) { sender.sendMessage(Component.text("You do not have permission to view TobysCamera status.")); return true; }
        var render = mediaActivation.status(); var upload = coordinator.status(); var totals = runtimeStatus.totals();
        sender.sendMessage(Component.text("TobysCamera status"));
        sender.sendMessage(Component.text("Rendering: " + render.activePhotoCount() + " photos, " + render.activeMapCount() + " maps"));
        sender.sendMessage(Component.text("Uploading: " + upload.activePhotoCount() + " photos, " + upload.activeTileCount() + " tiles, " + upload.reservedBytes() + "/" + upload.maxReservedBytes() + " bytes"));
        sender.sendMessage(Component.text("This run: " + totals.runPhotos() + " photos, " + totals.runTiles() + " tiles"));
        sender.sendMessage(Component.text("Stored: " + totals.storedPhotos() + " photos, " + totals.storedTiles() + " tiles"));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> {
            try {
                for (var photoId : new PendingDeliveryRepository(getDataFolder().toPath()).take(player.getUniqueId())) {
                    var record = repository.find(photoId);
                    if (record != null) deliveries.deliver(player, record);
                }
            } catch (IOException exception) { getLogger().warning("Could not deliver queued photos: " + exception.getMessage()); }
        }, () -> { });
    }
}
