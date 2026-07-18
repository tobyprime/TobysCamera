package dev.tobyscamera.folia.map;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class TileMapRenderer extends MapRenderer {
    private volatile byte[] pixels;
    private final Map<MapCanvas, Long> renderedRevisions = new WeakHashMap<>();
    private long revision;

    public TileMapRenderer() {
        super(false);
    }

    public TileMapRenderer(byte[] pixels) {
        this();
        setPixels(pixels);
    }

    public synchronized void setPixels(byte[] pixels) {
        if (pixels.length != 16_384) throw new IllegalArgumentException("map tile must contain 16384 bytes");
        if (Arrays.equals(this.pixels, pixels)) return;
        this.pixels = pixels.clone();
        revision++;
    }

    public synchronized void clearPixels() {
        pixels = null;
        revision++;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        byte[] current;
        long currentRevision;
        synchronized (this) {
            current = pixels;
            currentRevision = revision;
            Long renderedRevision = renderedRevisions.get(canvas);
            if (renderedRevision != null && renderedRevision == currentRevision) return;
        }
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            canvas.setPixel(x, y, current == null ? 0 : current[y * 128 + x]);
        }
        synchronized (this) {
            if (revision == currentRevision && pixels == current) renderedRevisions.put(canvas, currentRevision);
        }
    }
}
