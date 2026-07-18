package dev.tobyscamera.folia.video;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MapUpdateDispatcherTest {
    @Test
    void sendsChangedMapsOnEachViewerEntityScheduler() {
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        Player player = mock(Player.class);
        MapView map = mock(MapView.class);
        MapUpdateDispatcher dispatcher = new MapUpdateDispatcher(scheduler);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        dispatcher.send(map, List.of(player));

        verify(scheduler).runEntity(eq(player), task.capture(), any());
        task.getValue().run();
        verify(player).sendMap(map);
    }
}
