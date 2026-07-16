package dev.tobyscamera.folia.video;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

/** Uses Bukkit's normal map packet path after a dynamic renderer has changed pixels. */
public final class MapUpdateDispatcher {
    private final Plugin plugin;

    public MapUpdateDispatcher(Plugin plugin) { this.plugin = plugin; }

    public void send(MapView map, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) viewer.getScheduler().run(plugin, ignored -> viewer.sendMap(map), () -> { });
    }
}
