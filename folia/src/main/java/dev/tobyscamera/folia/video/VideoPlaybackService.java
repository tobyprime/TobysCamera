package dev.tobyscamera.folia.video;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tobyscamera.folia.map.MapVideoService;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.Plugin;

/** Global playback uses region-owned event snapshots; it never reads entity state from the global scheduler. */
public final class VideoPlaybackService implements Listener {
    private final Plugin plugin;
    private final ServerTaskScheduler scheduler;
    private final MapVideoService videos;
    private final int activeLimit;
    private final int maximumDistance;
    private final VideoPlaybackClock clock = new VideoPlaybackClock();
    private final VideoPlaybackIndex index = new VideoPlaybackIndex();
    private final MapUpdateDispatcher mapUpdates;
    private final VideoFrameLoadRequests frameLoads = new VideoFrameLoadRequests();
    private final Map<UUID, IndexedPlayer> players = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastSentFrame = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReadErrorAt = new ConcurrentHashMap<>();
    private final AtomicLong serverTick = new AtomicLong();

    public VideoPlaybackService(Plugin plugin, ServerTaskScheduler scheduler, MapVideoService videos, int activeLimit, int maximumDistance) {
        this.plugin = plugin; this.scheduler = scheduler; this.videos = videos; this.activeLimit = activeLimit; this.maximumDistance = maximumDistance; this.mapUpdates = new MapUpdateDispatcher(scheduler);
    }

    /** Schedules each loaded chunk on its owning region before touching its entities. */
    public void indexLoadedFrames() {
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX(), chunkZ = chunk.getZ();
            scheduler.runRegion(world, chunkX, chunkZ, () -> {
                for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) if (entity instanceof ItemFrame frame) index(frame, frame.getItem());
            });
        }
    }

    @EventHandler public void onEntityAdded(EntityAddToWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) index(frame, frame.getItem()); }
    @EventHandler public void onEntityRemoved(EntityRemoveFromWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) index.removeFrame(frame.getUniqueId()); }
    @EventHandler public void onItemFrameChanged(PlayerItemFrameChangeEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) index.removeFrame(event.getItemFrame().getUniqueId());
        else index(event.getItemFrame(), event.getItemStack());
    }
    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { indexPlayer(event.getPlayer(), event.getPlayer().getLocation()); }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { players.remove(event.getPlayer().getUniqueId()); index.removeViewer(event.getPlayer().getUniqueId()); }
    @EventHandler public void onPlayerMove(PlayerMoveEvent event) { indexPlayer(event.getPlayer(), event.getTo()); }
    @EventHandler public void onPlayerItemHeld(PlayerItemHeldEvent event) { refreshHeldMapsNextTick(event.getPlayer()); }
    @EventHandler public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) { refreshHeldMapsNextTick(event.getPlayer()); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) refreshHeldMapsNextTick(player); }
    @EventHandler public void onItemPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) refreshHeldMapsNextTick(player); }

    /** Must be called after plugin code changes the map of an existing item frame. */
    public void refreshFrame(ItemFrame frame) { index(frame, frame.getItem()); }

    /** Re-indexes only transient player snapshots and current loaded frames after lazy activation. */
    public void refreshActiveMedia() {
        for (IndexedPlayer indexed : players.values()) {
            scheduler.runEntity(indexed.player(), () -> indexPlayer(indexed.player(), indexed.player().getLocation()), () -> { });
        }
        indexLoadedFrames();
    }

    /** Runs globally: only immutable snapshots and player scheduler handles are used here. */
    public void tick() {
        long tick = serverTick.getAndIncrement();
        Map<Integer, Set<UUID>> activeViewers = index.activeViewers(activeLimit, maximumDistance);
        lastSentFrame.keySet().retainAll(activeViewers.keySet());
        for (var active : activeViewers.entrySet()) {
            int mapId = active.getKey();
            var tile = videos.tileForMap(mapId); if (tile == null) continue;
            var record = videos.activeRecord(tile.videoId()); if (record == null) continue;
            if (!clock.shouldUpdateAtTick(record.fps(), tick)) continue;
            int frameIndex = clock.frameAtTick(record.frameCount(), record.fps(), tick);
            if (Integer.valueOf(frameIndex).equals(lastSentFrame.get(mapId))) continue;
            var map = videos.showCachedFrame(record, mapId, frameIndex);
            if (map == null) {
                preload(record, mapId, frameIndex);
                continue;
            }
            lastSentFrame.put(mapId, frameIndex);
            mapUpdates.send(map, viewers(active.getValue()));
        }
    }

    private void preload(dev.tobyscamera.folia.storage.VideoRecord record, int mapId, int frameIndex) {
        VideoFrameLoadRequests.Key key = new VideoFrameLoadRequests.Key(record.videoId(), mapId, frameIndex);
        if (!frameLoads.begin(key)) return;
        scheduler.runAsync(() -> {
            try {
                videos.preloadFrame(record, mapId, frameIndex);
            } catch (IOException exception) {
                logReadFailure(record.videoId(), exception);
            } finally {
                frameLoads.complete(key);
            }
        });
    }

    private void logReadFailure(UUID videoId, IOException exception) {
        long now = System.currentTimeMillis();
        Long last = lastReadErrorAt.putIfAbsent(videoId, now);
        if (last == null || now - last >= 60_000L) {
            lastReadErrorAt.put(videoId, now);
            plugin.getLogger().warning("Could not load video frame " + videoId + ": " + exception.getMessage());
        }
    }

    private void index(ItemFrame frame, ItemStack item) {
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) { index.removeFrame(frame.getUniqueId()); return; }
        int mapId = meta.getMapView().getId();
        if (videos.tileForMap(mapId) == null) { index.removeFrame(frame.getUniqueId()); return; }
        index.upsertFrame(frame.getUniqueId(), mapId, frame.getWorld().getUID(), frame.getX(), frame.getY(), frame.getZ());
    }

    private void indexPlayer(Player player, Location location) {
        if (location == null || location.getWorld() == null) return;
        Set<Integer> heldMaps = heldVideoMaps(player);
        IndexedPlayer previous = players.put(player.getUniqueId(), new IndexedPlayer(player, location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), heldMaps));
        index.upsertViewer(player.getUniqueId(), location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), heldMaps);
        if (previous == null || !previous.heldMapIds().equals(heldMaps)) for (int mapId : heldMaps) lastSentFrame.remove(mapId);
    }

    private void refreshHeldMapsNextTick(Player player) {
        scheduler.runEntityDelayed(player, 1L, () -> indexPlayer(player, player.getLocation()), () -> { });
    }

    private List<Player> viewers(Set<UUID> viewerIds) {
        return viewerIds.stream().map(players::get).filter(java.util.Objects::nonNull)
                .map(IndexedPlayer::player).toList();
    }

    private Set<Integer> heldVideoMaps(Player player) {
        var maps = new java.util.HashSet<Integer>();
        addHeldMap(maps, player.getInventory().getItemInMainHand());
        addHeldMap(maps, player.getInventory().getItemInOffHand());
        return Set.copyOf(maps);
    }

    private void addHeldMap(Set<Integer> maps, ItemStack item) {
        if (item.getItemMeta() instanceof MapMeta meta && meta.hasMapView() && videos.tileForMap(meta.getMapView().getId()) != null) maps.add(meta.getMapView().getId());
    }

    private record IndexedPlayer(Player player, UUID worldId, double x, double y, double z, Set<Integer> heldMapIds) { }
}
