package dev.tobyscamera.fabric.camera;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.material.MapColor;

public final class MapTileEncoder {
    public EncodedPhoto encode(CapturedFrame frame) {
        return encode(frame, DitheringMode.OFF);
    }

    public EncodedPhoto encode(CapturedFrame frame, DitheringMode ditheringMode) {
        return encode(frame.image(), frame.gridSize(), ditheringMode);
    }

    public EncodedPhoto encode(BufferedImage source) {
        return encode(source, DitheringMode.OFF);
    }

    public EncodedPhoto encode(BufferedImage source, DitheringMode ditheringMode) {
        int gridWidth = Math.max(1, (source.getWidth() + 127) / 128);
        int gridHeight = Math.max(1, (source.getHeight() + 127) / 128);
        return encode(source, gridWidth, gridHeight, ditheringMode);
    }

    public BufferedImage palettePreview(BufferedImage source, DitheringMode ditheringMode) {
        return palettePreview(encode(source, ditheringMode));
    }

    public BufferedImage palettePreview(EncodedPhoto photo) {
        BufferedImage preview = new BufferedImage(photo.gridWidth() * 128, photo.gridHeight() * 128, BufferedImage.TYPE_INT_ARGB);
        for (int tileY = 0; tileY < photo.gridHeight(); tileY++) for (int tileX = 0; tileX < photo.gridWidth(); tileX++) {
            byte[] tile = photo.tiles().get(tileY * photo.gridWidth() + tileX);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int color = MapColor.getColorFromPackedId(Byte.toUnsignedInt(tile[y * 128 + x]));
                preview.setRGB(tileX * 128 + x, tileY * 128 + y, 0xff000000 | color & 0x00ffffff);
            }
        }
        return preview;
    }

    private EncodedPhoto encode(BufferedImage source, int gridSize, DitheringMode ditheringMode) {
        int expectedSize = gridSize * 128;
        if (source.getWidth() != expectedSize || source.getHeight() != expectedSize) {
            throw new IllegalArgumentException("prepared frame size must match server grid");
        }
        return encode(source, gridSize, gridSize, ditheringMode);
    }

    private EncodedPhoto encode(BufferedImage source, int gridWidth, int gridHeight, DitheringMode ditheringMode) {
        if (ditheringMode == null) throw new IllegalArgumentException("dithering mode is required");
        int targetWidth = gridWidth * 128;
        int targetHeight = gridHeight * 128;
        byte[] pixels = ditheringMode == DitheringMode.FLOYD_STEINBERG
                ? floydSteinbergPixels(source, targetWidth, targetHeight)
                : nearestColorPixels(source, targetWidth, targetHeight);
        List<byte[]> tiles = new ArrayList<>(gridWidth * gridHeight);
        for (int tileY = 0; tileY < gridHeight; tileY++) for (int tileX = 0; tileX < gridWidth; tileX++) {
            byte[] tile = new byte[16_384];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                tile[y * 128 + x] = pixels[(tileY * 128 + y) * targetWidth + tileX * 128 + x];
            }
            tiles.add(tile);
        }
        return new EncodedPhoto(gridWidth, gridHeight, List.copyOf(tiles));
    }

    private static byte[] nearestColorPixels(BufferedImage source, int targetWidth, int targetHeight) {
        byte[] pixels = new byte[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) for (int x = 0; x < targetWidth; x++) {
            pixels[y * targetWidth + x] = nearestMapColor(source.getRGB(sourceX(source, x, targetWidth), sourceY(source, y, targetHeight)));
        }
        return pixels;
    }

    private static byte[] floydSteinbergPixels(BufferedImage source, int targetWidth, int targetHeight) {
        byte[] pixels = new byte[targetWidth * targetHeight];
        double[] currentRed = new double[targetWidth + 2], currentGreen = new double[targetWidth + 2], currentBlue = new double[targetWidth + 2];
        double[] nextRed = new double[targetWidth + 2], nextGreen = new double[targetWidth + 2], nextBlue = new double[targetWidth + 2];
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int argb = source.getRGB(sourceX(source, x, targetWidth), sourceY(source, y, targetHeight));
                int red = clamp(((argb >>> 16) & 0xff) + currentRed[x + 1]);
                int green = clamp(((argb >>> 8) & 0xff) + currentGreen[x + 1]);
                int blue = clamp((argb & 0xff) + currentBlue[x + 1]);
                int id = nearestMapColorId(red, green, blue);
                pixels[y * targetWidth + x] = (byte) id;
                int palette = MapColor.getColorFromPackedId(id);
                distribute(red - ((palette >>> 16) & 0xff), currentRed, nextRed, x);
                distribute(green - ((palette >>> 8) & 0xff), currentGreen, nextGreen, x);
                distribute(blue - (palette & 0xff), currentBlue, nextBlue, x);
            }
            double[] previousRed = currentRed; currentRed = nextRed; nextRed = previousRed; Arrays.fill(nextRed, 0.0);
            double[] previousGreen = currentGreen; currentGreen = nextGreen; nextGreen = previousGreen; Arrays.fill(nextGreen, 0.0);
            double[] previousBlue = currentBlue; currentBlue = nextBlue; nextBlue = previousBlue; Arrays.fill(nextBlue, 0.0);
        }
        return pixels;
    }

    private static void distribute(double error, double[] current, double[] next, int x) {
        current[x + 2] += error * 7.0 / 16.0;
        next[x] += error * 3.0 / 16.0;
        next[x + 1] += error * 5.0 / 16.0;
        next[x + 2] += error / 16.0;
    }

    private static int sourceX(BufferedImage source, int x, int targetWidth) {
        return Math.min(source.getWidth() - 1, x * source.getWidth() / targetWidth);
    }

    private static int sourceY(BufferedImage source, int y, int targetHeight) {
        return Math.min(source.getHeight() - 1, y * source.getHeight() / targetHeight);
    }

    private static byte nearestMapColor(int argb) {
        return (byte) nearestMapColorId((argb >>> 16) & 0xff, (argb >>> 8) & 0xff, argb & 0xff);
    }

    private static int nearestMapColorId(int red, int green, int blue) {
        int bestDistance = Integer.MAX_VALUE, bestId = 1;
        // Packed map color 0 is the transparent sentinel, never a valid camera pixel.
        for (int id = 1; id < 256; id++) {
            int palette = MapColor.getColorFromPackedId(id);
            int dr = red - ((palette >>> 16) & 0xff), dg = green - ((palette >>> 8) & 0xff), db = blue - (palette & 0xff);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) { bestDistance = distance; bestId = id; }
        }
        return bestId;
    }

    private static int clamp(double value) { return (int) Math.max(0, Math.min(255, Math.round(value))); }

    public enum DitheringMode { OFF, FLOYD_STEINBERG }

    public record EncodedPhoto(int gridWidth, int gridHeight, List<byte[]> tiles) { }
}
