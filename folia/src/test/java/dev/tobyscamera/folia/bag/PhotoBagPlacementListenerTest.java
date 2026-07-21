package dev.tobyscamera.folia.bag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PhotoBagPlacementListenerTest {
    @Test
    void acceptsPreCancelledAirInteractEvents() throws NoSuchMethodException {
        EventHandler handler = PhotoBagPlacementListener.class
                .getMethod("onUseBag", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);

        assertFalse(handler.ignoreCancelled());
    }

    @Test
    void startsPendingUnpackOnlyWhenRightClickingAir() {
        ServerTaskScheduler scheduler = mock(ServerTaskScheduler.class);
        PhotoBagPlacementListener listener = new PhotoBagPlacementListener(mock(Plugin.class), mock(MapPhotoService.class), scheduler);
        Player player = mock(Player.class);
        ItemStack bag = mock(ItemStack.class);
        PhotoBagData data = new PhotoBagData(UUID.randomUUID(), PhotoBagKind.PHOTO, 7, 1, 1);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<PhotoBagFactory> bags = org.mockito.Mockito.mockStatic(PhotoBagFactory.class)) {
            bags.when(() -> PhotoBagFactory.isBag(bag)).thenReturn(true);
            bags.when(() -> PhotoBagFactory.isNegative(bag)).thenReturn(false);
            bags.when(() -> PhotoBagFactory.read(bag)).thenReturn(data);

            listener.onUseBag(interact(player, bag, Action.RIGHT_CLICK_BLOCK));

            verify(scheduler, never()).runEntityDelayed(eq(player), anyLong(), any(Runnable.class), any(Runnable.class));

            listener.onUseBag(interact(player, bag, Action.RIGHT_CLICK_AIR));

            verify(scheduler).runEntityDelayed(eq(player), eq(20L), any(Runnable.class), any(Runnable.class));
        }
    }

    private static PlayerInteractEvent interact(Player player, ItemStack item, Action action) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(action);
        when(event.getItem()).thenReturn(item);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
