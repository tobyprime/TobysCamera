package dev.tobyscamera.folia.map;

import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

public final class MapPhotoService {
    private final Plugin plugin;
    private final PhotoRepository repository;

    public MapPhotoService(Plugin plugin, PhotoRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public PhotoRecord createMaps(UUID ownerId, World world, UploadSession session) {
        UUID photoId = UUID.randomUUID();
        Map<TileCoordinate, Integer> mapIds = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y);
            byte[] pixels = session.tile(x, y);
            MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false); view.setUnlimitedTracking(false); view.setLocked(true);
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            view.addRenderer(new TileMapRenderer(pixels));
            mapIds.put(coordinate, view.getId());
        }
        PhotoRecord record = new PhotoRecord(photoId, ownerId, Instant.now(), session.width(), session.height(), mapIds);
        return record;
    }

    public void persist(PhotoRecord record, UploadSession session) throws IOException {
        Map<TileCoordinate, byte[]> tiles = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            tiles.put(new TileCoordinate(x, y), session.tile(x, y));
        }
        repository.save(record, tiles, session.previewPixels());
    }

    /** Removes this plugin's transient renderers from maps whose media storage failed. */
    public void discard(PhotoRecord record) {
        for (int mapId : record.mapIds().values()) {
            MapView view = Bukkit.getMap(mapId);
            if (view == null) continue;
            for (MapRenderer renderer : view.getRenderers()) if (renderer instanceof TileMapRenderer) view.removeRenderer(renderer);
        }
    }

    public PhotoRecord record(UUID photoId) throws IOException { return repository.find(photoId); }

    public ItemStack bag(World world, PhotoRecord record, UploadSession session) {
        return bag(world, record, session, null);
    }

    public ItemStack bag(World world, PhotoRecord record, UploadSession session, PhotoMetadata metadata) {
        return PhotoBagFactory.create(world, record.photoId(), PhotoBagKind.PHOTO, record.gridWidth(), record.gridHeight(),
                metadata,
                session.previewPixels());
    }

    public ItemStack bag(World world, PhotoRecord record) throws IOException {
        return bag(world, record, (PhotoMetadata) null);
    }

    public ItemStack bag(World world, PhotoRecord record, PhotoMetadata metadata) throws IOException {
        return PhotoBagFactory.create(world, record.photoId(), PhotoBagKind.PHOTO, record.gridWidth(), record.gridHeight(),
                metadata,
                previewPixels(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, 0, record.gridWidth(), record.gridHeight())));
    }

    /** Reads the client-generated preview persisted with the photo. Legacy bags are unsupported. */
    public byte[] previewPixels(PhotoBagData bag) throws IOException {
        if (bag.kind() != PhotoBagKind.PHOTO) throw new IllegalArgumentException("bag is not a photo");
        byte[] preview = repository.readPreview(bag.mediaId());
        if (preview == null || preview.length != 16_384) throw new IOException("photo bag preview is unavailable");
        return preview;
    }

    public void restore() throws IOException {
        for (PhotoRecord record : repository.loadAll()) for (var entry : record.mapIds().entrySet()) {
            MapView view = Bukkit.getMap(entry.getValue());
            if (view == null) continue;
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            view.addRenderer(new TileMapRenderer(repository.readTile(record.photoId(), entry.getKey())));
        }
    }

    public ItemStack mapItem(PhotoRecord record, TileCoordinate coordinate, PhotoMetadata metadata) {
        MapView view = Bukkit.getMap(record.mapIds().get(coordinate));
        if (view == null) throw new IllegalStateException("map no longer exists");
        ItemStack item = new ItemStack(org.bukkit.Material.FILLED_MAP);
        var meta = (org.bukkit.inventory.meta.MapMeta) item.getItemMeta();
        meta.setMapView(view);
        var presentation = MapItemPresentation.photo(coordinate, metadata);
        meta.displayName(presentation.name());
        meta.lore(presentation.lore());
        item.setItemMeta(meta);
        RootCustomData.update(item, tag -> {
            tag.putString("tobyscamera:photo_id", record.photoId().toString());
            tag.putInt("tobyscamera:tile_x", coordinate.x());
            tag.putInt("tobyscamera:tile_y", coordinate.y());
            tag.putInt("tobyscamera:grid_width", record.gridWidth());
            tag.putInt("tobyscamera:grid_height", record.gridHeight());
            if (metadata != null) {
                tag.putString("tobyscamera:photographer", metadata.photographer());
                tag.putString("tobyscamera:capture_world", metadata.world());
                tag.putInt("tobyscamera:capture_x", metadata.x());
                tag.putInt("tobyscamera:capture_y", metadata.y());
                tag.putInt("tobyscamera:capture_z", metadata.z());
                tag.putLong("tobyscamera:captured_at", metadata.capturedAt().toEpochMilli());
            }
        });
        return item;
    }

}
