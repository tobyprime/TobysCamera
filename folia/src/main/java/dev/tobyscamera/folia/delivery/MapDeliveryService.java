package dev.tobyscamera.folia.delivery;

import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.io.IOException;
import org.bukkit.entity.Player;

public final class MapDeliveryService {
    private final MapPhotoService maps;
    private final PendingDeliveryRepository pending;
    public MapDeliveryService(MapPhotoService maps, PendingDeliveryRepository pending) { this.maps = maps; this.pending = pending; }
    public void deliver(Player player, PhotoRecord record) throws IOException {
        for (TileCoordinate coordinate : record.mapIds().keySet()) {
            var leftovers = player.getInventory().addItem(maps.mapItem(record, coordinate));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }
    public void queue(Player player, PhotoRecord record) throws IOException { pending.add(player.getUniqueId(), record.photoId()); }
}
