package dev.tobyscamera.folia.map;

import java.util.Arrays;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/** A map renderer whose 128×128 palette pixels can be replaced by playback. */
public final class MutableTileMapRenderer extends MapRenderer {
    private volatile byte[] pixels;

    public MutableTileMapRenderer(byte[] pixels) {
        super(false);
        setPixels(pixels);
    }

    public void setPixels(byte[] pixels) {
        if (pixels.length != 16_384) throw new IllegalArgumentException("map tile must contain 16384 bytes");
        this.pixels = Arrays.copyOf(pixels, pixels.length);
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
