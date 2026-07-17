package dev.tobyscamera.folia.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** A single-file sequence of individually compressed standard map tiles. */
final class TileContainer {
    static final int TILE_BYTES = 16_384;
    private TileContainer() { }

    static Map<String, Range> write(Path destination, Map<String, byte[]> tiles) throws IOException {
        Map<String, Range> ranges = new LinkedHashMap<>();
        long offset = 0;
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (var entry : tiles.entrySet()) {
                byte[] compressed = compress(entry.getValue());
                output.write(compressed);
                ranges.put(entry.getKey(), new Range(offset, compressed.length));
                offset += compressed.length;
            }
        }
        return Map.copyOf(ranges);
    }

    static byte[] read(Path source, Range range) throws IOException {
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            return read(channel, range);
        }
    }

    static byte[] read(FileChannel channel, Range range) throws IOException {
        if (range.offset() < 0 || range.length() < 1) throw new IOException("invalid tile container range");
        ByteBuffer compressed = ByteBuffer.allocate(range.length());
        long position = range.offset();
        while (compressed.hasRemaining()) {
            int read = channel.read(compressed, position);
            if (read < 0) throw new IOException("truncated tile container");
            position += read;
        }
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed.array()))) {
            byte[] tile = input.readAllBytes();
            if (tile.length != TILE_BYTES) throw new IOException("invalid tile container payload length");
            return tile;
        }
    }

    private static byte[] compress(byte[] tile) throws IOException {
        if (tile.length != TILE_BYTES) throw new IOException("tile must be 16384 bytes");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(bytes)) { output.write(tile); }
        return bytes.toByteArray();
    }

    record Range(long offset, int length) { }
}
