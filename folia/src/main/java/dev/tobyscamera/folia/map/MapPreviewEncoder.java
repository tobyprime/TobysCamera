package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.storage.TileCoordinate;
import java.util.Objects;
import java.util.function.Function;

/** Downscales a complete palette-tile grid to the 128×128 pixels used by a photo-bag preview map. */
public final class MapPreviewEncoder {
    private MapPreviewEncoder() { }

    public static byte[] encode(int gridWidth, int gridHeight, Function<TileCoordinate, byte[]> tiles) {
        if (gridWidth < 1 || gridHeight < 1) throw new IllegalArgumentException("grid dimensions must be positive");
        Objects.requireNonNull(tiles, "tiles");
        int sourceWidth = gridWidth * 128;
        int sourceHeight = gridHeight * 128;
        byte[] preview = new byte[16_384];
        for (int y = 0; y < 128; y++) {
            int sourceY = Math.min(sourceHeight - 1, (int) (((long) y * sourceHeight) / 128));
            int tileY = sourceY / 128;
            int pixelY = sourceY % 128;
            for (int x = 0; x < 128; x++) {
                int sourceX = Math.min(sourceWidth - 1, (int) (((long) x * sourceWidth) / 128));
                int tileX = sourceX / 128;
                int pixelX = sourceX % 128;
                byte[] tile = Objects.requireNonNull(tiles.apply(new TileCoordinate(tileX, tileY)), "tile is missing");
                if (tile.length != 16_384) throw new IllegalArgumentException("tile must contain 16384 palette pixels");
                preview[y * 128 + x] = tile[pixelY * 128 + pixelX];
            }
        }
        return preview;
    }
}
