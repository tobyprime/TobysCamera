package dev.tobyscamera.folia.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class TileMapRenderer extends MapRenderer {
    private volatile byte[] pixels;

    public TileMapRenderer() {
        super(false);
    }

    public TileMapRenderer(byte[] pixels) {
        this();
        setPixels(pixels);
    }

    public void setPixels(byte[] pixels) {
        if (pixels.length != 16_384) throw new IllegalArgumentException("map tile must contain 16384 bytes");
        this.pixels = pixels.clone();
    }

    public void clearPixels() {
        pixels = null;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        byte[] current = pixels;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            canvas.setPixel(x, y, current == null ? 0 : current[y * 128 + x]);
        }
    }
}
