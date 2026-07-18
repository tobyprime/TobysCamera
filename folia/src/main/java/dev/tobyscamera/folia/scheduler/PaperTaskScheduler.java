package dev.tobyscamera.folia.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

/** Uses Paper's single main thread for all world and entity work. */
public final class PaperTaskScheduler implements ServerTaskScheduler {
    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public PaperTaskScheduler(Plugin plugin) {
        this(plugin, Bukkit.getScheduler());
    }

    PaperTaskScheduler(Plugin plugin, BukkitScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public void runGlobal(Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public TaskHandle runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
        var scheduled = scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks);
        return scheduled::cancel;
    }

    @Override
    public void runAsync(Runnable task) {
        scheduler.runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runEntity(Player player, Runnable task, Runnable retired) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runEntityDelayed(Player player, long delayTicks, Runnable task, Runnable retired) {
        scheduler.runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runRegion(World world, int chunkX, int chunkZ, Runnable task) {
        scheduler.runTask(plugin, task);
    }
}
