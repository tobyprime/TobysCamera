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
import dev.tobyscamera.folia.bag.PhotoBagPlacementListener;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.delivery.PendingDeliveryRepository;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.SqlitePhotoRepository;
import dev.tobyscamera.folia.upload.UploadCoordinator;
import dev.tobyscamera.folia.status.PluginRuntimeStatus;
import dev.tobyscamera.folia.map.MediaMapActivationListener;
import dev.tobyscamera.folia.map.VirtualMapDeliveryScheduler;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import dev.tobyscamera.folia.scheduler.ServerTaskSchedulers;
import dev.tobyscamera.folia.gallery.PhotoGalleryListener;
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
    private PhotoGalleryListener gallery;

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
        gallery = new PhotoGalleryListener(repository, photos, mediaActivation, scheduler);
        getServer().getPluginManager().registerEvents(gallery, this);
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
                player -> player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.3f),
                this::isUploadBlocked);
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
        if (args[0].equalsIgnoreCase("gallery")) { if (sender instanceof Player player) gallery.open(player); else sender.sendMessage(Component.text("This command must be run by a player.")); return true; }
        if (args[0].equalsIgnoreCase("status")) return status(sender);
        if (args[0].equalsIgnoreCase("detail")) return detail(sender);
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

    private boolean isUploadBlocked(java.util.UUID playerId) {
        try {
            return repository.isBlocked(playerId);
        } catch (IOException exception) {
            getLogger().warning("Could not check photo upload block: " + exception.getMessage());
            return true;
        }
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
                var record = photos.createMaps(player.getUniqueId(), player.getName(), world, session, metadata);
                scheduler.runAsync(() -> {
                    try {
                        photos.persist(record, session);
                        runtimeStatus.recordPersisted(record.mapIds().size());
                        scheduler.runGlobal(() -> {
                            scheduler.runEntity(player, () -> {
                                deliveries.deliver(player, record);
                                send(player, new Packets.PhotoCreated(record.photoId(), record.mapIds().values().stream().toList(), record.gridWidth(), record.gridHeight()));
                            }, () -> { try { deliveries.queue(player, record); } catch (IOException exception) { getLogger().warning("Could not queue photo delivery: " + exception.getMessage()); } });
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

    private boolean detail(CommandSender sender) {
        if (!sender.hasPermission("tobyscamera.detail")) { sender.sendMessage(Component.text("You do not have permission to view TobysCamera photo details.")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("This command must be run by a player holding a photo bag.")); return true; }
        var item = PhotoBagFactory.isBag(player.getInventory().getItemInMainHand()) ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (!PhotoBagFactory.isBag(item)) { sender.sendMessage(Component.text("Hold a TobysCamera photo bag in either hand.")); return true; }
        try {
            var bag = PhotoBagFactory.read(item);
            for (var line : PhotoBagFactory.adminDetails(bag)) sender.sendMessage(line);
            var record = repository.find(bag.mediaId());
            if (record == null) { sender.sendMessage(Component.text("Stored photo record: missing")); return true; }
            sender.sendMessage(Component.text("Storage owner UUID: " + record.ownerId()));
            sender.sendMessage(Component.text("Storage created: " + record.createdAt()));
            String mapIds = record.mapIds().entrySet().stream()
                    .sorted(java.util.Comparator.comparingInt((java.util.Map.Entry<dev.tobyscamera.folia.storage.TileCoordinate, Integer> entry) -> entry.getKey().y())
                            .thenComparingInt(entry -> entry.getKey().x()))
                    .map(entry -> "(" + entry.getKey().x() + "," + entry.getKey().y() + ")=#" + entry.getValue()).collect(java.util.stream.Collectors.joining(", "));
            sender.sendMessage(Component.text("Printed map IDs: " + mapIds));
        } catch (IllegalArgumentException exception) { sender.sendMessage(Component.text("The held photo bag has invalid data.")); }
        catch (IOException exception) { sender.sendMessage(Component.text("Could not read the stored photo record.")); }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> {
            try {
                deliveries.deliverPending(player, repository::find);
            } catch (IOException exception) { getLogger().warning("Could not deliver queued photos: " + exception.getMessage()); }
        }, () -> { });
    }
}
