package dev.tobyscamera.fabric.camera;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.material.MapColor;

public final class MapTileEncoder {
    public EncodedPhoto encode(BufferedImage source) {
        int gridWidth = Math.min(4, Math.max(1, (source.getWidth() + 127) / 128));
        int gridHeight = Math.min(4, Math.max(1, (source.getHeight() + 127) / 128));
        int targetWidth = gridWidth * 128;
        int targetHeight = gridHeight * 128;
        List<byte[]> tiles = new ArrayList<>(gridWidth * gridHeight);
        for (int tileY = 0; tileY < gridHeight; tileY++) for (int tileX = 0; tileX < gridWidth; tileX++) {
            byte[] tile = new byte[16_384];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int sourceX = Math.min(source.getWidth() - 1, (tileX * 128 + x) * source.getWidth() / targetWidth);
                int sourceY = Math.min(source.getHeight() - 1, (tileY * 128 + y) * source.getHeight() / targetHeight);
                tile[y * 128 + x] = nearestMapColor(source.getRGB(sourceX, sourceY));
            }
            tiles.add(tile);
        }
        return new EncodedPhoto(gridWidth, gridHeight, List.copyOf(tiles));
    }

    private static byte nearestMapColor(int argb) {
        int red = (argb >>> 16) & 0xff, green = (argb >>> 8) & 0xff, blue = argb & 0xff;
        int bestDistance = Integer.MAX_VALUE, bestId = 0;
        for (int id = 0; id < 256; id++) {
            int palette = MapColor.getColorFromPackedId(id);
            int dr = red - ((palette >>> 16) & 0xff), dg = green - ((palette >>> 8) & 0xff), db = blue - (palette & 0xff);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) { bestDistance = distance; bestId = id; }
        }
        return (byte) bestId;
    }

    public record EncodedPhoto(int gridWidth, int gridHeight, List<byte[]> tiles) { }
}
