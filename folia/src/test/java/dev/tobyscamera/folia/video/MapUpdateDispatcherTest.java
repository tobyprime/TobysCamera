package dev.tobyscamera.folia.video;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;

class MapUpdateDispatcherTest {
    @Test
    void sendsTheChangedMapToEveryRelevantViewer() {
        Player first = mock(Player.class); Player second = mock(Player.class); MapView map = mock(MapView.class);

        new MapUpdateDispatcher().send(map, List.of(first, second));

        verify(first).sendMap(map);
        verify(second).sendMap(map);
    }
}
