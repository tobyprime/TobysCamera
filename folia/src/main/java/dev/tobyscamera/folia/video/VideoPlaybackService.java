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
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

/** Indexes loaded video item frames and refreshes only the distance-budgeted map IDs. */
public final class VideoPlaybackService implements Listener {
    private final MapVideoService videos;
    private final int activeLimit;
    private final VideoPlaybackClock clock;
    private final ActiveVideoMapSelector selector = new ActiveVideoMapSelector();
    private final Map<UUID, IndexedFrame> frames = new ConcurrentHashMap<>();

    public VideoPlaybackService(MapVideoService videos, int activeLimit, long startedAtMillis) {
        this.videos = videos; this.activeLimit = activeLimit; this.clock = new VideoPlaybackClock(startedAtMillis);
    }

    /** One initial scan covers frames already loaded before plugin enable; updates are event driven afterwards. */
    public void indexLoadedFrames() {
        for (var world : Bukkit.getWorlds()) for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) index(frame, frame.getItem());
    }

    @EventHandler public void onEntityAdded(EntityAddToWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) index(frame, frame.getItem()); }
    @EventHandler public void onEntityRemoved(EntityRemoveFromWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) frames.remove(frame.getUniqueId()); }
    @EventHandler public void onItemFrameChanged(PlayerItemFrameChangeEvent event) {
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) frames.remove(event.getItemFrame().getUniqueId());
        else index(event.getItemFrame(), event.getItemStack());
    }

    public void tick() {
        List<ActiveVideoMapSelector.Point> players = Bukkit.getOnlinePlayers().stream().map(player -> {
            var location = player.getLocation(); return new ActiveVideoMapSelector.Point(player.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
        }).toList();
        List<ActiveVideoMapSelector.Candidate> candidates = new ArrayList<>();
        for (IndexedFrame frame : frames.values()) candidates.add(new ActiveVideoMapSelector.Candidate(frame.mapId(), frame.worldId(), frame.x(), frame.y(), frame.z()));
        long now = System.currentTimeMillis();
        for (var candidate : selector.select(candidates, players, activeLimit)) {
            var tile = videos.tileForMap(candidate.mapId()); if (tile == null) continue;
            var record = videos.record(tile.videoId()); if (record == null) continue;
            try { videos.showFrame(record, candidate.mapId(), clock.frameAt(record.frameCount(), record.fps(), now)); }
            catch (IOException ignored) { }
        }
    }

    private void index(ItemFrame frame, ItemStack item) {
        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) { frames.remove(frame.getUniqueId()); return; }
        int mapId = meta.getMapView().getId();
        if (videos.tileForMap(mapId) == null) { frames.remove(frame.getUniqueId()); return; }
        frames.put(frame.getUniqueId(), new IndexedFrame(mapId, frame.getWorld().getUID(), frame.getX(), frame.getY(), frame.getZ()));
    }

    private record IndexedFrame(int mapId, UUID worldId, double x, double y, double z) { }
}
