package dev.tobyscamera.folia.video;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tobyscamera.folia.map.MapVideoService;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final MapVideoService videos;
    private final int activeLimit;
    private final double maximumDistanceSquared;
    private final VideoPlaybackClock clock = new VideoPlaybackClock();
    private final ActiveVideoMapSelector selector = new ActiveVideoMapSelector();
    private final MapUpdateDispatcher mapUpdates;
    private final Map<UUID, IndexedFrame> frames = new ConcurrentHashMap<>();
    private final Map<UUID, IndexedPlayer> players = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastSentFrame = new ConcurrentHashMap<>();
    private final AtomicLong serverTick = new AtomicLong();

    public VideoPlaybackService(Plugin plugin, MapVideoService videos, int activeLimit, int maximumDistance) {
        this.plugin = plugin; this.videos = videos; this.activeLimit = activeLimit; this.maximumDistanceSquared = (double) maximumDistance * maximumDistance; this.mapUpdates = new MapUpdateDispatcher(plugin);
    }

    /** Schedules each loaded chunk on its owning region before touching its entities. */
    public void indexLoadedFrames() {
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX(), chunkZ = chunk.getZ();
            Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, ignored -> {
                for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) if (entity instanceof ItemFrame frame) index(frame, frame.getItem());
            });
        }
    }

    @EventHandler public void onEntityAdded(EntityAddToWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) index(frame, frame.getItem()); }
    @EventHandler public void onEntityRemoved(EntityRemoveFromWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) frames.remove(frame.getUniqueId()); }
    @EventHandler public void onItemFrameChanged(PlayerItemFrameChangeEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) frames.remove(event.getItemFrame().getUniqueId());
        else index(event.getItemFrame(), event.getItemStack());
    }
    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { indexPlayer(event.getPlayer(), event.getPlayer().getLocation()); }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { players.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onPlayerMove(PlayerMoveEvent event) { indexPlayer(event.getPlayer(), event.getTo()); }
    @EventHandler public void onPlayerItemHeld(PlayerItemHeldEvent event) { refreshHeldMapsNextTick(event.getPlayer()); }
    @EventHandler public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) { refreshHeldMapsNextTick(event.getPlayer()); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) refreshHeldMapsNextTick(player); }
    @EventHandler public void onItemPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) refreshHeldMapsNextTick(player); }

    /** Must be called after plugin code changes the map of an existing item frame. */
    public void refreshFrame(ItemFrame frame) { index(frame, frame.getItem()); }

    /** Runs globally: only immutable snapshots and player scheduler handles are used here. */
    public void tick() {
        long tick = serverTick.getAndIncrement();
        List<ActiveVideoMapSelector.Point> points = players.values().stream().map(player -> new ActiveVideoMapSelector.Point(player.worldId(), player.x(), player.y(), player.z())).toList();
        List<ActiveVideoMapSelector.Candidate> candidates = new ArrayList<>();
        for (IndexedFrame frame : frames.values()) candidates.add(new ActiveVideoMapSelector.Candidate(frame.mapId(), frame.worldId(), frame.x(), frame.y(), frame.z()));
        for (IndexedPlayer player : players.values()) for (int mapId : player.heldMapIds()) candidates.add(new ActiveVideoMapSelector.Candidate(mapId, player.worldId(), player.x(), player.y(), player.z()));
        var active = new LinkedHashMap<Integer, ActiveVideoMapSelector.Candidate>();
        for (var candidate : selector.select(candidates, points, activeLimit, maximumDistanceSquared)) active.putIfAbsent(candidate.mapId(), candidate);
        for (var candidate : active.values()) {
            var tile = videos.tileForMap(candidate.mapId()); if (tile == null) continue;
            var record = videos.record(tile.videoId()); if (record == null) continue;
            if (!clock.shouldUpdateAtTick(record.fps(), tick)) continue;
            int frameIndex = clock.frameAtTick(record.frameCount(), record.fps(), tick);
            if (Integer.valueOf(frameIndex).equals(lastSentFrame.put(candidate.mapId(), frameIndex))) continue;
            try {
                var map = videos.showFrame(record, candidate.mapId(), frameIndex);
                if (map != null) mapUpdates.send(map, viewers(candidate.mapId()));
            } catch (IOException ignored) { }
        }
    }

    private void index(ItemFrame frame, ItemStack item) {
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) { frames.remove(frame.getUniqueId()); return; }
        int mapId = meta.getMapView().getId();
        if (videos.tileForMap(mapId) == null) { frames.remove(frame.getUniqueId()); return; }
        frames.put(frame.getUniqueId(), new IndexedFrame(mapId, frame.getWorld().getUID(), frame.getX(), frame.getY(), frame.getZ()));
    }

    private void indexPlayer(Player player, Location location) {
        if (location == null || location.getWorld() == null) return;
        Set<Integer> heldMaps = heldVideoMaps(player);
        IndexedPlayer previous = players.put(player.getUniqueId(), new IndexedPlayer(player, player.getScheduler(), location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), heldMaps));
        if (previous == null || !previous.heldMapIds().equals(heldMaps)) for (int mapId : heldMaps) lastSentFrame.remove(mapId);
    }

    private void refreshHeldMapsNextTick(Player player) {
        player.getScheduler().runDelayed(plugin, ignored -> indexPlayer(player, player.getLocation()), () -> { }, 1L);
    }

    private List<MapUpdateDispatcher.Viewer> viewers(int mapId) {
        return players.values().stream().filter(player -> player.heldMapIds().contains(mapId)
                || frames.values().stream().anyMatch(frame -> frame.mapId() == mapId && player.worldId().equals(frame.worldId())
                && distanceSquared(frame.x(), frame.y(), frame.z(), player.x(), player.y(), player.z()) <= maximumDistanceSquared))
                .map(player -> new MapUpdateDispatcher.Viewer(player.player(), player.scheduler())).toList();
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

    private static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) { double x = x1 - x2, y = y1 - y2, z = z1 - z2; return x * x + y * y + z * z; }

    private record IndexedFrame(int mapId, UUID worldId, double x, double y, double z) { }
    private record IndexedPlayer(Player player, io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler, UUID worldId, double x, double y, double z, Set<Integer> heldMapIds) { }
}
