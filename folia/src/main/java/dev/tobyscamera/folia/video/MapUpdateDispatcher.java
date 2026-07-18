package dev.tobyscamera.folia.video;

import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

/** Uses Bukkit's normal map packet path after a dynamic renderer has changed pixels. */
public final class MapUpdateDispatcher {
    private final ServerTaskScheduler scheduler;

    public MapUpdateDispatcher(ServerTaskScheduler scheduler) { this.scheduler = scheduler; }

    public void send(MapView map, Collection<Player> viewers) {
        for (Player viewer : viewers) scheduler.runEntity(viewer, () -> viewer.sendMap(map), () -> { });
    }
}
