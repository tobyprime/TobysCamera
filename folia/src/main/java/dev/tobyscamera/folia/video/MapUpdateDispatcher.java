package dev.tobyscamera.folia.video;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

/** Uses Bukkit's normal map packet path after a dynamic renderer has changed pixels. */
public final class MapUpdateDispatcher {
    private final Plugin plugin;

    public MapUpdateDispatcher(Plugin plugin) { this.plugin = plugin; }

    public void send(MapView map, Collection<Viewer> viewers) {
        for (Viewer viewer : viewers) viewer.scheduler().run(plugin, ignored -> viewer.player().sendMap(map), () -> { });
    }

    public record Viewer(Player player, EntityScheduler scheduler) { }
}
