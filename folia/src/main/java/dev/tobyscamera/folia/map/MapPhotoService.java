package dev.tobyscamera.folia.map;

import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.PhotoCoordinates;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MapPhotoService {
    private final Plugin plugin;
    private final PhotoRepository repository;

    public MapPhotoService(Plugin plugin, PhotoRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public PhotoRecord createMaps(UUID ownerId, World world, PhotoCoordinates coordinates, UploadSession session) {
        UUID photoId = UUID.randomUUID();
        Map<TileCoordinate, Integer> mapIds = new LinkedHashMap<>();
        Map<TileCoordinate, byte[]> tiles = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y);
            byte[] pixels = session.tile(x, y);
            MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false); view.setUnlimitedTracking(false); view.setLocked(true);
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            view.addRenderer(new TileMapRenderer(pixels));
            mapIds.put(coordinate, view.getId()); tiles.put(coordinate, pixels);
        }
        PhotoRecord record = new PhotoRecord(photoId, ownerId, Instant.now(), coordinates, session.width(), session.height(), mapIds);
        return record;
    }

    public void persist(PhotoRecord record, UploadSession session) throws IOException {
        Map<TileCoordinate, byte[]> tiles = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            tiles.put(new TileCoordinate(x, y), session.tile(x, y));
        }
        repository.save(record, tiles);
    }

    public void restore() throws IOException {
        for (PhotoRecord record : repository.loadAll()) for (var entry : record.mapIds().entrySet()) {
            MapView view = Bukkit.getMap(entry.getValue());
            if (view == null) continue;
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            view.addRenderer(new TileMapRenderer(repository.readTile(record.photoId(), entry.getKey())));
        }
    }

    public ItemStack mapItem(PhotoRecord record, TileCoordinate coordinate) {
        MapView view = Bukkit.getMap(record.mapIds().get(coordinate));
        if (view == null) throw new IllegalStateException("map no longer exists");
        ItemStack item = new ItemStack(org.bukkit.Material.FILLED_MAP);
        var meta = (org.bukkit.inventory.meta.MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.lore(java.util.List.of(Component.text("拍摄坐标: " + record.coordinates().display(), NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        item.editPersistentDataContainer(container -> {
            container.set(new NamespacedKey("tobyscamera", "photo_id"), PersistentDataType.STRING, record.photoId().toString());
            container.set(new NamespacedKey("tobyscamera", "tile_x"), PersistentDataType.INTEGER, coordinate.x());
            container.set(new NamespacedKey("tobyscamera", "tile_y"), PersistentDataType.INTEGER, coordinate.y());
            container.set(new NamespacedKey("tobyscamera", "grid_width"), PersistentDataType.INTEGER, record.gridWidth());
            container.set(new NamespacedKey("tobyscamera", "grid_height"), PersistentDataType.INTEGER, record.gridHeight());
        });
        return item;
    }
}
