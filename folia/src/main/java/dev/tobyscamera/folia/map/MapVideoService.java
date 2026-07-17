package dev.tobyscamera.folia.map;

import dev.tobyscamera.common.upload.VideoUploadSession;
import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.storage.VideoRecord;
import dev.tobyscamera.folia.storage.VideoRepository;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class MapVideoService {
    private final VideoRepository repository;
    private final Map<Integer, MutableTileMapRenderer> renderers = new ConcurrentHashMap<>();
    private final Map<Integer, VideoTile> tilesByMapId = new ConcurrentHashMap<>();
    private final Map<UUID, VideoRecord> recordsById = new ConcurrentHashMap<>();
    private final VideoTileCache tileCache = new VideoTileCache(2_048);

    public MapVideoService(VideoRepository repository) { this.repository = repository; }

    public VideoRecord createMaps(UUID ownerId, World world, VideoUploadSession session) {
        UUID videoId = UUID.randomUUID(); Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y); MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false); view.setUnlimitedTracking(false); view.setLocked(true);
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            MutableTileMapRenderer renderer = new MutableTileMapRenderer(session.tile(0, x, y));
            view.addRenderer(renderer); maps.put(coordinate, view.getId());
            renderers.put(view.getId(), renderer); tilesByMapId.put(view.getId(), new VideoTile(videoId, coordinate));
        }
        VideoRecord record = new VideoRecord(videoId, ownerId, Instant.now(), session.width(), session.height(), session.fps(), session.frameCount(), maps);
        recordsById.put(record.videoId(), record);
        return record;
    }

    public void persist(VideoRecord record, VideoUploadSession session) throws IOException { repository.save(record, session); }

    public ItemStack bag(World world, VideoRecord record, VideoUploadSession session) {
        return PhotoBagFactory.create(world, record.videoId(), PhotoBagKind.VIDEO, record.gridWidth(), record.gridHeight(),
                MapPreviewEncoder.encode(record.gridWidth(), record.gridHeight(), coordinate -> session.tile(0, coordinate.x(), coordinate.y())));
    }

    /** Recreates a bag-preview palette from frame zero in durable video storage. */
    public byte[] previewPixels(PhotoBagData bag) throws IOException {
        if (bag.kind() != PhotoBagKind.VIDEO) throw new IllegalArgumentException("bag is not a video");
        try {
            return MapPreviewEncoder.encode(bag.gridWidth(), bag.gridHeight(), coordinate -> {
                try { return repository.readTile(bag.mediaId(), 0, coordinate); }
                catch (IOException exception) { throw new PreviewReadFailure(exception); }
            });
        } catch (PreviewReadFailure exception) {
            throw exception.getCause();
        }
    }

    public void restore() throws IOException {
        for (VideoRecord record : repository.loadAll()) {
            recordsById.put(record.videoId(), record);
            for (var entry : record.mapIds().entrySet()) {
            MapView view = Bukkit.getMap(entry.getValue()); if (view == null) continue;
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            MutableTileMapRenderer renderer = new MutableTileMapRenderer(repository.readTile(record.videoId(), 0, entry.getKey()));
            view.addRenderer(renderer); renderers.put(view.getId(), renderer); tilesByMapId.put(view.getId(), new VideoTile(record.videoId(), entry.getKey()));
            }
        }
    }

    public MapView showFrame(VideoRecord record, int mapId, int frameIndex) throws IOException {
        VideoTile tile = tilesByMapId.get(mapId); MutableTileMapRenderer renderer = renderers.get(mapId);
        if (tile == null || renderer == null || !tile.videoId().equals(record.videoId())) return null;
        renderer.setPixels(tileCache.get(new VideoTileCache.Key(record.videoId(), frameIndex, tile.coordinate()),
                () -> repository.readTile(record.videoId(), frameIndex, tile.coordinate())));
        return Bukkit.getMap(mapId);
    }

    public VideoTile tileForMap(int mapId) { return tilesByMapId.get(mapId); }
    public VideoRecord record(UUID videoId) { return recordsById.get(videoId); }

    public ItemStack mapItem(VideoRecord record, TileCoordinate coordinate, PhotoMetadata metadata) {
        MapView view = Bukkit.getMap(record.mapIds().get(coordinate)); if (view == null) throw new IllegalStateException("map no longer exists");
        ItemStack item = new ItemStack(org.bukkit.Material.FILLED_MAP); MapMeta meta = (MapMeta) item.getItemMeta(); meta.setMapView(view); item.setItemMeta(meta);
        var presentation = MapItemPresentation.video(coordinate, metadata);
        meta.displayName(presentation.name());
        meta.lore(presentation.lore());
        item.setItemMeta(meta);
        RootCustomData.update(item, tag -> { tag.putString("tobyscamera:video_id", record.videoId().toString()); tag.putInt("tobyscamera:tile_x", coordinate.x()); tag.putInt("tobyscamera:tile_y", coordinate.y()); tag.putInt("tobyscamera:grid_width", record.gridWidth()); tag.putInt("tobyscamera:grid_height", record.gridHeight()); if (metadata != null) { tag.putString("tobyscamera:photographer", metadata.photographer()); tag.putString("tobyscamera:capture_world", metadata.world()); tag.putInt("tobyscamera:capture_x", metadata.x()); tag.putInt("tobyscamera:capture_y", metadata.y()); tag.putInt("tobyscamera:capture_z", metadata.z()); tag.putLong("tobyscamera:captured_at", metadata.capturedAt().toEpochMilli()); } });
        return item;
    }

    public record VideoTile(UUID videoId, TileCoordinate coordinate) { }

    private static final class PreviewReadFailure extends RuntimeException {
        private PreviewReadFailure(IOException cause) { super(cause); }
        @Override public IOException getCause() { return (IOException) super.getCause(); }
    }
}
