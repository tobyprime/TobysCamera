package dev.tobyscamera.folia.scheduler;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Proxy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class ServerTaskSchedulersTest {
    @Test
    void usesTheRegionSafeSchedulerForPaperAndFolia() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Plugin.class },
                (proxy, method, arguments) -> null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));

            assertInstanceOf(FoliaTaskScheduler.class, ServerTaskSchedulers.create(plugin));
        }
    }
}
