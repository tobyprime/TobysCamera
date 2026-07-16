package dev.tobyscamera.folia.video;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tobyscamera.folia.map.MapVideoService;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.Plugin;

/** Global playback uses region-owned event snapshots; it never reads entity state from the global scheduler. */
public final class VideoPlaybackService implements Listener {
    private final Plugin plugin;
    private final MapVideoService videos;
    private final int activeLimit;
    private final VideoPlaybackClock clock = new VideoPlaybackClock();
    private final ActiveVideoMapSelector selector = new ActiveVideoMapSelector();
    private final MapUpdateDispatcher mapUpdates;
    private final Map<UUID, IndexedFrame> frames = new ConcurrentHashMap<>();
    private final Map<UUID, IndexedPlayer> players = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastSentFrame = new ConcurrentHashMap<>();
    private final AtomicLong serverTick = new AtomicLong();

    public VideoPlaybackService(Plugin plugin, MapVideoService videos, int activeLimit) {
        this.plugin = plugin; this.videos = videos; this.activeLimit = activeLimit; this.mapUpdates = new MapUpdateDispatcher(plugin);
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

    /** Runs globally: only immutable snapshots and player scheduler handles are used here. */
    public void tick() {
        long tick = serverTick.getAndIncrement();
        List<ActiveVideoMapSelector.Point> points = players.values().stream().map(player -> new ActiveVideoMapSelector.Point(player.worldId(), player.x(), player.y(), player.z())).toList();
        List<ActiveVideoMapSelector.Candidate> candidates = new ArrayList<>();
        for (IndexedFrame frame : frames.values()) candidates.add(new ActiveVideoMapSelector.Candidate(frame.mapId(), frame.worldId(), frame.x(), frame.y(), frame.z()));
        for (var candidate : selector.select(candidates, points, activeLimit)) {
            var tile = videos.tileForMap(candidate.mapId()); if (tile == null) continue;
            var record = videos.record(tile.videoId()); if (record == null) continue;
            if (!clock.shouldUpdateAtTick(record.fps(), tick)) continue;
            int frameIndex = clock.frameAtTick(record.frameCount(), record.fps(), tick);
            if (Integer.valueOf(frameIndex).equals(lastSentFrame.put(candidate.mapId(), frameIndex))) continue;
            try {
                var map = videos.showFrame(record, candidate.mapId(), frameIndex);
                if (map != null) mapUpdates.send(map, players.values().stream().filter(player -> player.worldId().equals(candidate.worldId())).map(player -> new MapUpdateDispatcher.Viewer(player.player(), player.scheduler())).toList());
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
        players.put(player.getUniqueId(), new IndexedPlayer(player, player.getScheduler(), location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()));
    }

    private record IndexedFrame(int mapId, UUID worldId, double x, double y, double z) { }
    private record IndexedPlayer(Player player, io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler, UUID worldId, double x, double y, double z) { }
}
