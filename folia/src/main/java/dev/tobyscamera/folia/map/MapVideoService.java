package dev.tobyscamera.folia.map;

import dev.tobyscamera.common.upload.VideoUploadSession;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.storage.MediaTileCache;
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

/** Video maps and frame bytes exist only while a tagged map item is active. */
public final class MapVideoService {
    private static final int RECORD_CACHE_SIZE = 256;
    private final VideoRepository repository;
    private final MediaTileCache cache;
    private final Map<String, ActiveTile> activeBySource = new ConcurrentHashMap<>();
    private final Map<Integer, ActiveTile> activeByMapId = new ConcurrentHashMap<>();
    private final Map<UUID, VideoRecord> activeRecords = new ConcurrentHashMap<>();
    private final Map<UUID, VideoRecord> records = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, VideoRecord> eldest) { return size() > RECORD_CACHE_SIZE; }
    };

    public MapVideoService(VideoRepository repository) {
        this(repository, new MediaTileCache(64L * 1024L * 1024L));
    }

    public MapVideoService(VideoRepository repository, MediaTileCache cache) { this.repository = repository; this.cache = cache; }

    public VideoRecord createMaps(UUID ownerId, World world, VideoUploadSession session) {
        UUID videoId = UUID.randomUUID();
        Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
        for (int y = 0; y < session.height(); y++) for (int x = 0; x < session.width(); x++) {
            TileCoordinate coordinate = new TileCoordinate(x, y);
            MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false); view.setUnlimitedTracking(false); view.setLocked(true);
            for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
            maps.put(coordinate, view.getId());
        }
        return new VideoRecord(videoId, ownerId, Instant.now(), session.width(), session.height(), session.fps(), session.frameCount(), maps);
    }

    public void persist(VideoRecord record, VideoUploadSession session) throws IOException { repository.save(record, session); }

    /** Bukkit cannot reclaim IDs created for a failed persistence attempt. */
    public void discard(VideoRecord record) {
        for (int mapId : record.mapIds().values()) detachMap(mapId);
    }

    public ItemStack bag(World world, VideoRecord record, VideoUploadSession session) { return bag(world, record, session, null); }

    public ItemStack bag(World world, VideoRecord record, VideoUploadSession session, PhotoMetadata metadata) {
        return PhotoBagFactory.create(world, record.videoId(), PhotoBagKind.VIDEO, record.gridWidth(), record.gridHeight(), metadata, session.previewPixels());
    }

    public byte[] previewPixels(PhotoBagData bag) throws IOException {
        if (bag.kind() != PhotoBagKind.VIDEO) throw new IllegalArgumentException("bag is not a video");
        byte[] preview = cache.getOrLoad(MediaTileCache.Key.videoPreview(bag.mediaId()), () -> repository.readPreview(bag.mediaId()));
        if (preview == null || preview.length != 16_384) throw new IOException("video bag preview is unavailable");
        return preview;
    }

    /** Performs storage I/O and must be called only from an async scheduler. */
    public LoadedVideo load(MediaMapDescriptor.VideoTile tile) throws IOException {
        VideoRecord record = findRecord(tile.mediaId());
        if (record == null) throw new IOException("video does not exist");
        if (!Integer.valueOf(tile.mapId()).equals(record.mapIds().get(tile.coordinate()))) throw new IOException("video map identity does not match storage");
        return new LoadedVideo(record, cache.getOrLoad(MediaTileCache.Key.videoTile(tile.mediaId(), 0, tile.coordinate()),
                () -> repository.readTile(tile.mediaId(), 0, tile.coordinate())));
    }

    /** Installs one active renderer after {@link #load} completes. */
    public synchronized void attach(String source, MediaMapDescriptor.VideoTile tile, LoadedVideo loaded) {
        ActiveTile prior = activeBySource.get(source);
        if (prior != null && prior.mapId == tile.mapId()) return;
        if (prior != null) detach(source);
        ActiveTile active = activeByMapId.get(tile.mapId());
        if (active == null) {
            MapView map = Bukkit.getMap(tile.mapId());
            if (map == null) return;
            MutableTileMapRenderer renderer = new MutableTileMapRenderer(loaded.firstFrame());
            for (MapRenderer existingRenderer : map.getRenderers()) map.removeRenderer(existingRenderer);
            map.addRenderer(renderer);
            active = new ActiveTile(tile.mapId(), tile, loaded.record(), map, renderer);
            activeByMapId.put(tile.mapId(), active);
            activeRecords.put(loaded.record().videoId(), loaded.record());
        }
        active.sources.put(source, Boolean.TRUE);
        activeBySource.put(source, active);
    }

    public synchronized void detach(String source) {
        ActiveTile active = activeBySource.remove(source);
        if (active == null) return;
        active.sources.remove(source);
        if (!active.sources.isEmpty()) return;
        detachMap(active.mapId);
    }

    public synchronized void clear() {
        for (String source : java.util.Set.copyOf(activeBySource.keySet())) detach(source);
    }

    private void detachMap(int mapId) {
        ActiveTile active = activeByMapId.remove(mapId);
        if (active == null) return;
        for (String source : active.sources.keySet()) activeBySource.remove(source, active);
        active.renderer.clearPixels();
        active.map.removeRenderer(active.renderer);
        if (activeByMapId.values().stream().noneMatch(other -> other.record.videoId().equals(active.record.videoId()))) {
            activeRecords.remove(active.record.videoId(), active.record);
        }
    }

    /** Reads a requested active tile into the shared cache; call from async scheduling only. */
    public void preloadFrame(VideoRecord record, int mapId, int frameIndex) throws IOException {
        ActiveTile active = activeByMapId.get(mapId);
        if (active == null || !active.record.videoId().equals(record.videoId())) return;
        cache.getOrLoad(MediaTileCache.Key.videoTile(record.videoId(), frameIndex, active.tile.coordinate()),
                () -> repository.readTile(record.videoId(), frameIndex, active.tile.coordinate()));
    }

    /** Applies a previously cached video frame without performing storage I/O. */
    public MapView showCachedFrame(VideoRecord record, int mapId, int frameIndex) {
        ActiveTile active = activeByMapId.get(mapId);
        if (active == null || !active.record.videoId().equals(record.videoId())) return null;
        byte[] pixels = cache.find(MediaTileCache.Key.videoTile(record.videoId(), frameIndex, active.tile.coordinate()));
        if (pixels == null) return null;
        active.renderer.setPixels(pixels);
        return active.map;
    }

    public VideoTile tileForMap(int mapId) {
        ActiveTile active = activeByMapId.get(mapId);
        return active == null ? null : new VideoTile(active.tile.mediaId(), active.tile.coordinate());
    }

    public VideoRecord activeRecord(UUID videoId) { return activeRecords.get(videoId); }
    public VideoRecord record(UUID videoId) throws IOException { return findRecord(videoId); }

    private VideoRecord findRecord(UUID videoId) throws IOException {
        synchronized (records) {
            VideoRecord cached = records.get(videoId);
            if (cached != null) return cached;
            VideoRecord loaded = repository.find(videoId);
            if (loaded != null) records.put(videoId, loaded);
            return loaded;
        }
    }

    public ItemStack mapItem(VideoRecord record, TileCoordinate coordinate, PhotoMetadata metadata) {
        MapView view = Bukkit.getMap(record.mapIds().get(coordinate));
        if (view == null) throw new IllegalStateException("map no longer exists");
        ItemStack item = new ItemStack(org.bukkit.Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta(); meta.setMapView(view); item.setItemMeta(meta);
        var presentation = MapItemPresentation.video(coordinate, metadata);
        meta.displayName(presentation.name()); meta.lore(presentation.lore()); item.setItemMeta(meta);
        RootCustomData.update(item, tag -> {
            tag.putString("tobyscamera:video_id", record.videoId().toString());
            tag.putInt("tobyscamera:tile_x", coordinate.x()); tag.putInt("tobyscamera:tile_y", coordinate.y());
            tag.putInt("tobyscamera:grid_width", record.gridWidth()); tag.putInt("tobyscamera:grid_height", record.gridHeight());
            if (metadata != null) {
                tag.putString("tobyscamera:photographer", metadata.photographer()); tag.putString("tobyscamera:capture_world", metadata.world());
                tag.putInt("tobyscamera:capture_x", metadata.x()); tag.putInt("tobyscamera:capture_y", metadata.y()); tag.putInt("tobyscamera:capture_z", metadata.z()); tag.putLong("tobyscamera:captured_at", metadata.capturedAt().toEpochMilli());
            }
        });
        return item;
    }

    public record VideoTile(UUID videoId, TileCoordinate coordinate) { }
    public record LoadedVideo(VideoRecord record, byte[] firstFrame) { }
    private static final class ActiveTile {
        private final int mapId;
        private final MediaMapDescriptor.VideoTile tile;
        private final VideoRecord record;
        private final MapView map;
        private final MutableTileMapRenderer renderer;
        private final Map<String, Boolean> sources = new java.util.HashMap<>();
        private ActiveTile(int mapId, MediaMapDescriptor.VideoTile tile, VideoRecord record, MapView map, MutableTileMapRenderer renderer) {
            this.mapId = mapId; this.tile = tile; this.record = record; this.map = map; this.renderer = renderer;
        }
    }
}
