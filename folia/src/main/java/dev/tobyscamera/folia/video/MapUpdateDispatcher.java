package dev.tobyscamera.folia.video;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

/** Uses Bukkit's normal map packet path after a dynamic renderer has changed pixels. */
public final class MapUpdateDispatcher {
    public void send(MapView map, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) viewer.sendMap(map);
    }
}
