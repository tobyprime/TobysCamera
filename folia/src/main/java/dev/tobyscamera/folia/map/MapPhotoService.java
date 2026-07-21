package dev.tobyscamera.folia.map;

import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.MediaTileCache;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.craftbukkit.CraftWorld;

public final class MapPhotoService {
    private final Plugin plugin;
    private final PhotoRepository repository;
    private final MediaTileCache cache;
    private final VirtualMapIdAllocator virtualMapIds;

    public MapPhotoService(Plugin plugin, PhotoRepository repository) {
        this(plugin, repository, new MediaTileCache(64L * 1024L * 1024L),
                () -> ((CraftWorld) plugin.getServer().getWorlds().getFirst()).getHandle().getFreeMapId().id());
    }

    public MapPhotoService(Plugin plugin, PhotoRepository repository, MediaTileCache cache) {
        this(plugin, repository, cache,
                () -> ((CraftWorld) plugin.getServer().getWorlds().getFirst()).getHandle().getFreeMapId().id());
    }

    MapPhotoService(Plugin plugin, PhotoRepository repository, MediaTileCache cache,
            java.util.function.IntSupplier nextVanillaMapId) {
        this.plugin = plugin;
        this.repository = repository;
        this.cache = cache;
        this.virtualMapIds = new VirtualMapIdAllocator(nextVanillaMapId);
    }

    public PhotoRecord createMaps(UUID ownerId, World world, UploadSession session) {
        UUID photoId = UUID.randomUUID();
        Map<TileCoordinate, Integer> mapIds = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y);
            mapIds.put(coordinate, virtualMapIds.allocate());
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

    /** Bukkit has no API to reclaim map IDs created for a failed save. */
    public void discard(PhotoRecord record) {
    }

    public PhotoRecord record(UUID photoId) throws IOException { return repository.find(photoId); }

    public ItemStack bag(World world, PhotoRecord record, UploadSession session) {
        return bag(world, record, session, null);
    }

    public ItemStack bag(World world, PhotoRecord record, UploadSession session, PhotoMetadata metadata) {
        return PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, virtualMapIds.allocate(),
                record.gridWidth(), record.gridHeight(), metadata));
    }

    public ItemStack bag(World world, PhotoRecord record) throws IOException {
        return bag(world, record, (PhotoMetadata) null);
    }

    public ItemStack bag(World world, PhotoRecord record, PhotoMetadata metadata) throws IOException {
        return PhotoBagFactory.createNegative(new PhotoBagData(record.photoId(), PhotoBagKind.PHOTO, virtualMapIds.allocate(),
                record.gridWidth(), record.gridHeight(), metadata));
    }

    /** Reads the client-generated preview persisted with the photo. Legacy bags are unsupported. */
    public byte[] previewPixels(PhotoBagData bag) throws IOException {
        if (bag.kind() != PhotoBagKind.PHOTO) throw new IllegalArgumentException("bag is not a photo");
        byte[] preview = cache.getOrLoad(MediaTileCache.Key.photoPreview(bag.mediaId()), () -> repository.readPreview(bag.mediaId()));
        if (preview == null || preview.length != 16_384) throw new IOException("photo bag preview is unavailable");
        return preview;
    }

    public byte[] tilePixels(MediaMapDescriptor.PhotoTile tile) throws IOException {
        return cache.getOrLoad(MediaTileCache.Key.photoTile(tile.mediaId(), tile.coordinate()),
                () -> repository.readTile(tile.mediaId(), tile.coordinate()));
    }

    public ItemStack mapItem(PhotoRecord record, TileCoordinate coordinate, PhotoMetadata metadata) {
        ItemStack item = new ItemStack(org.bukkit.Material.FILLED_MAP);
        var meta = (org.bukkit.inventory.meta.MapMeta) item.getItemMeta();
        meta.setMapId(record.mapIds().get(coordinate));
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
                var photoPresentation = metadata.presentation();
                if (!photoPresentation.name().isEmpty()) tag.putString("tobyscamera:photo_name", photoPresentation.name());
                if (!photoPresentation.description().isEmpty()) tag.putString("tobyscamera:description", photoPresentation.description());
                tag.putBoolean("tobyscamera:public_address", photoPresentation.publicAddress());
                tag.putBoolean("tobyscamera:public_photographer", photoPresentation.publicPhotographer());
                tag.putBoolean("tobyscamera:public_captured_time", photoPresentation.publicCapturedTime());
            }
        });
        return item;
    }

}
