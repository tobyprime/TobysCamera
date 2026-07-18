package dev.tobyscamera.folia.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaperTaskSchedulerTest {
    private final Plugin plugin = mock(Plugin.class);
    private final BukkitScheduler bukkit = mock(BukkitScheduler.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private PaperTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaperTaskScheduler(plugin, bukkit);
    }

    @Test
    void runsEntityWorkOnTheBukkitScheduler() {
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        scheduler.runEntity(player, executed::incrementAndGet, retired::incrementAndGet);

        verify(bukkit).runTask(eq(plugin), task.capture());
        task.getValue().run();
        assertEquals(1, executed.get());
        assertEquals(0, retired.get());
    }

    @Test
    void schedulesRegionAndAsyncWorkWithTheBukkitScheduler() {
        Runnable region = mock(Runnable.class);
        Runnable async = mock(Runnable.class);

        scheduler.runRegion(world, 4, -2, region);
        scheduler.runAsync(async);

        verify(bukkit).runTask(plugin, region);
        verify(bukkit).runTaskAsynchronously(plugin, async);
    }

    @Test
    void cancelsTheBukkitTaskForRepeatingGlobalWork() {
        Runnable task = mock(Runnable.class);
        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkit.runTaskTimer(plugin, task, 1L, 20L)).thenReturn(bukkitTask);

        ServerTaskScheduler.TaskHandle handle = scheduler.runGlobalRepeating(1L, 20L, task);
        handle.cancel();

        verify(bukkitTask).cancel();
    }

    @Test
    void delaysEntityWorkWithTheBukkitScheduler() {
        Runnable task = mock(Runnable.class);

        scheduler.runEntityDelayed(player, 3L, task, () -> { });

        verify(bukkit).runTaskLater(plugin, task, 3L);
    }
}
