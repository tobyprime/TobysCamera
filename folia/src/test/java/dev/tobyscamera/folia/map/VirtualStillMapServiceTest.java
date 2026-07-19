package dev.tobyscamera.folia.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class VirtualStillMapServiceTest {
    @Test
    void sendsOneFullMapToTheSamePlayerWhenTwoSourcesShareATile() {
        List<Runnable> async = new ArrayList<>();
        List<Runnable> sync = new ArrayList<>();
        VirtualMapPacketSender sender = mock(VirtualMapPacketSender.class);
        VirtualStillMapService service = new VirtualStillMapService(async::add, sync::add, ignored -> { }, sender);
        Player player = mock(Player.class);
        MediaMapDescriptor tile = new MediaMapDescriptor.PhotoTile(91, UUID.randomUUID(), new dev.tobyscamera.folia.storage.TileCoordinate(0, 0));

        service.attach("first", player, tile, VirtualMapDeliveryScheduler.Priority.MAIN_HAND, 0L, () -> new byte[16_384]);
        service.tick();
        async.removeFirst().run();
        sync.removeFirst().run();
        service.tick();
        service.attach("second", player, tile, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, () -> new byte[16_384]);

        verify(sender, times(1)).sendFull(eq(player), eq(91), any(byte[].class));
    }
}
