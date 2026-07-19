package dev.tobyscamera.folia.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MediaMapActivationListenerTest {
    @Test
    void refreshHeldMapsDefersTheHandRecheckUntilAfterTheInventoryMutation() {
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        MediaMapActivationListener listener = new MediaMapActivationListener(mock(Plugin.class), scheduler, mock(MapPhotoService.class));

        listener.refreshHeldMaps(mock(Player.class));

        verify(scheduler).runEntityDelayed(any(Player.class), eq(1L), any(Runnable.class), any(Runnable.class));
    }

    @Test
    void refreshVisibleFramesScansChunksAlreadySentBeforeThePluginWasEnabled() {
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        MediaMapActivationListener listener = new MediaMapActivationListener(mock(Plugin.class), scheduler, mock(MapPhotoService.class));
        Player player = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        org.bukkit.Location location = mock(org.bukkit.Location.class);
        org.mockito.ArgumentCaptor<Runnable> delayed = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.when(player.getSentChunks()).thenReturn(Set.of(chunk));
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(chunk.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(player.getLocation()).thenReturn(location);
        org.mockito.Mockito.when(location.getBlockX()).thenReturn(0);
        org.mockito.Mockito.when(location.getBlockZ()).thenReturn(0);
        org.mockito.Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(chunk.getX()).thenReturn(4);
        org.mockito.Mockito.when(chunk.getZ()).thenReturn(-2);

        listener.refreshVisibleFrames(player);

        verify(scheduler).runEntityDelayed(eq(player), eq(1L), delayed.capture(), any(Runnable.class));
        delayed.getValue().run();
        verify(scheduler).runRegion(eq(world), eq(4), eq(-2), any(Runnable.class));
    }
}
