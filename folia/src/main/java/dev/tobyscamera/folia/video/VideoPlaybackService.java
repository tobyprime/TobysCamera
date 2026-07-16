package dev.tobyscamera.folia.video;

import dev.tobyscamera.folia.map.MapVideoService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.meta.MapMeta;

/** Refreshes individual displayed video maps.  It deliberately does not require a complete grid to be visible. */
public final class VideoPlaybackService {
    private final MapVideoService videos;
    private final int activeLimit;
    private final VideoPlaybackClock clock;
    private final ActiveVideoMapSelector selector = new ActiveVideoMapSelector();

    public VideoPlaybackService(MapVideoService videos, int activeLimit, long startedAtMillis) {
        this.videos = videos; this.activeLimit = activeLimit; this.clock = new VideoPlaybackClock(startedAtMillis);
    }

    public void tick() {
        List<ActiveVideoMapSelector.Point> players = Bukkit.getOnlinePlayers().stream()
                .map(player -> player.getLocation()).map(location -> new ActiveVideoMapSelector.Point(location.getX(), location.getY(), location.getZ())).toList();
        List<ActiveVideoMapSelector.Candidate> candidates = new ArrayList<>();
        for (var world : Bukkit.getWorlds()) for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
            if (!(frame.getItem().getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) continue;
            int mapId = meta.getMapView().getId();
            if (videos.tileForMap(mapId) != null) candidates.add(new ActiveVideoMapSelector.Candidate(mapId, frame.getX(), frame.getY(), frame.getZ()));
        }
        long now = System.currentTimeMillis();
        for (var candidate : selector.select(candidates, players, activeLimit)) {
            var tile = videos.tileForMap(candidate.mapId()); var record = videos.record(tile.videoId());
            if (record == null) continue;
            try { videos.showFrame(record, candidate.mapId(), clock.frameAt(record.frameCount(), record.fps(), now)); }
            catch (IOException ignored) { }
        }
    }
}
