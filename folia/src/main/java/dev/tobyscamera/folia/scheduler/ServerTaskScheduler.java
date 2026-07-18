package dev.tobyscamera.folia.scheduler;

import org.bukkit.World;
import org.bukkit.entity.Player;

/** Schedules plugin work according to the hosting server's threading model. */
public interface ServerTaskScheduler {
    void runGlobal(Runnable task);

    TaskHandle runGlobalRepeating(long delayTicks, long periodTicks, Runnable task);

    void runAsync(Runnable task);

    void runEntity(Player player, Runnable task, Runnable retired);

    void runEntityDelayed(Player player, long delayTicks, Runnable task, Runnable retired);

    void runRegion(World world, int chunkX, int chunkZ, Runnable task);

    interface TaskHandle {
        void cancel();
    }
}
