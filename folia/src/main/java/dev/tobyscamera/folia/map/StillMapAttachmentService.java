package dev.tobyscamera.folia.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;

/** Owns lazily loaded still-map renderers only while one or more sources are active. */
public final class StillMapAttachmentService {
    private final IntFunction<MapView> maps;
    private final Consumer<Runnable> async;
    private final Consumer<Runnable> sync;
    private final Consumer<IOException> failures;
    private final Map<String, Attachment> attachmentsBySource = new HashMap<>();
    private final Map<Integer, Attachment> attachmentsByMapId = new HashMap<>();

    public StillMapAttachmentService(IntFunction<MapView> maps, Consumer<Runnable> async, Consumer<Runnable> sync,
            Consumer<IOException> failures) {
        this.maps = maps;
        this.async = async;
        this.sync = sync;
        this.failures = failures;
    }

    public synchronized void attach(String source, MediaMapDescriptor descriptor, PixelLoader loader) {
        Attachment existing = attachmentsBySource.get(source);
        if (existing != null && existing.mapId == descriptor.mapId()) return;
        if (existing != null) detach(source);
        Attachment attachment = attachmentsByMapId.get(descriptor.mapId());
        if (attachment == null) {
            MapView map = maps.apply(descriptor.mapId());
            if (map == null) return;
            TileMapRenderer renderer = new TileMapRenderer();
            for (MapRenderer existingRenderer : map.getRenderers()) map.removeRenderer(existingRenderer);
            map.addRenderer(renderer);
            attachment = new Attachment(descriptor.mapId(), map, renderer);
            attachmentsByMapId.put(descriptor.mapId(), attachment);
            startLoad(attachment, loader);
        }
        attachment.sources.add(source);
        attachmentsBySource.put(source, attachment);
    }

    public synchronized void detach(String source) {
        Attachment attachment = attachmentsBySource.remove(source);
        if (attachment == null) return;
        attachment.sources.remove(source);
        if (!attachment.sources.isEmpty()) return;
        attachment.active = false;
        attachment.renderer.clearPixels();
        attachment.map.removeRenderer(attachment.renderer);
        attachmentsByMapId.remove(attachment.mapId, attachment);
    }

    public synchronized void clear() {
        for (String source : Set.copyOf(attachmentsBySource.keySet())) detach(source);
    }

    private void startLoad(Attachment attachment, PixelLoader loader) {
        async.accept(() -> {
            byte[] pixels = null;
            IOException failure = null;
            try {
                pixels = loader.load();
            } catch (IOException exception) {
                failure = exception;
            }
            byte[] result = pixels;
            IOException error = failure;
            sync.accept(() -> apply(attachment, result, error));
        });
    }

    private synchronized void apply(Attachment attachment, byte[] pixels, IOException failure) {
        if (!attachment.active || attachmentsByMapId.get(attachment.mapId) != attachment) return;
        if (failure != null) {
            failures.accept(failure);
            return;
        }
        if (pixels == null) {
            failures.accept(new IOException("media tile is unavailable"));
            return;
        }
        attachment.renderer.setPixels(pixels);
    }

    private static final class Attachment {
        private final int mapId;
        private final MapView map;
        private final TileMapRenderer renderer;
        private final Set<String> sources = new HashSet<>();
        private boolean active = true;

        private Attachment(int mapId, MapView map, TileMapRenderer renderer) {
            this.mapId = mapId;
            this.map = map;
            this.renderer = renderer;
        }
    }

    @FunctionalInterface
    public interface PixelLoader {
        byte[] load() throws IOException;
    }
}
