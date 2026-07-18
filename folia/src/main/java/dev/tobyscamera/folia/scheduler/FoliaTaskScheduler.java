package dev.tobyscamera.folia.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Uses Paper's fallback schedulers on Paper and region ownership on Folia-compatible servers. */
public final class FoliaTaskScheduler implements ServerTaskScheduler {
    private final Plugin plugin;

    public FoliaTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    @Override
    public TaskHandle runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
        var scheduled = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> task.run(), delayTicks, periodTicks);
        return scheduled::cancel;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public void runEntity(Player player, Runnable task, Runnable retired) {
        player.getScheduler().run(plugin, ignored -> task.run(), retired);
    }

    @Override
    public void runEntityDelayed(Player player, long delayTicks, Runnable task, Runnable retired) {
        player.getScheduler().runDelayed(plugin, ignored -> task.run(), retired, delayTicks);
    }

    @Override
    public void runRegion(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, ignored -> task.run());
    }
}
