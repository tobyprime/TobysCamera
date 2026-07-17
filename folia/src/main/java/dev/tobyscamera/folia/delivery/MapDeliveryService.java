package dev.tobyscamera.folia.delivery;

import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class MapDeliveryService {
    private final MapPhotoService maps;
    private final PendingDeliveryRepository pending;
    private final Map<java.util.UUID, PhotoMetadata> transientMetadata = new ConcurrentHashMap<>();
    public MapDeliveryService(MapPhotoService maps, PendingDeliveryRepository pending) { this.maps = maps; this.pending = pending; }
    public void deliver(Player player, PhotoRecord record) throws IOException {
        deliver(player, record, transientMetadata.remove(record.photoId()));
    }
    public void deliver(Player player, PhotoRecord record, PhotoMetadata metadata) throws IOException {
        MapItemDelivery.deliver(java.util.List.of(maps.bag(player.getWorld(), record)),
                player.getInventory()::addItem, item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
    public void queue(Player player, PhotoRecord record, PhotoMetadata metadata) throws IOException {
        if (metadata != null) transientMetadata.put(record.photoId(), metadata);
        pending.add(player.getUniqueId(), record.photoId());
    }
}
