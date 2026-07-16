package dev.tobyscamera.folia.video;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MapUpdateDispatcherTest {
    @Test
    void sendsTheChangedMapToEveryRelevantViewer() {
        Player first = mock(Player.class); Player second = mock(Player.class); MapView map = mock(MapView.class);
        Plugin plugin = mock(Plugin.class); EntityScheduler firstScheduler = mock(EntityScheduler.class); EntityScheduler secondScheduler = mock(EntityScheduler.class);
        doAnswer(invocation -> { ((java.util.function.Consumer<?>) invocation.getArgument(1)).accept(null); return null; }).when(firstScheduler).run(org.mockito.ArgumentMatchers.same(plugin), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> { ((java.util.function.Consumer<?>) invocation.getArgument(1)).accept(null); return null; }).when(secondScheduler).run(org.mockito.ArgumentMatchers.same(plugin), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        new MapUpdateDispatcher(plugin).send(map, List.of(new MapUpdateDispatcher.Viewer(first, firstScheduler), new MapUpdateDispatcher.Viewer(second, secondScheduler)));

        verify(first).sendMap(map);
        verify(second).sendMap(map);
    }
}
