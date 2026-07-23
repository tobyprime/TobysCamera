package dev.tobyscamera.folia.delivery;

import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.storage.PhotoRecord;
import java.io.IOException;
import org.bukkit.entity.Player;

public final class MapDeliveryService {
    private final MapPhotoService maps;
    private final PendingDeliveryRepository pending;
    public MapDeliveryService(MapPhotoService maps, PendingDeliveryRepository pending) { this.maps = maps; this.pending = pending; }
    public void deliver(Player player, PhotoRecord record) {
        MapItemDelivery.deliver(java.util.List.of(maps.originalBag(player.getWorld(), record)),
                player.getInventory()::addItem, item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
    public void queue(Player player, PhotoRecord record) throws IOException {
        pending.add(player.getUniqueId(), record.photoId());
    }
}
