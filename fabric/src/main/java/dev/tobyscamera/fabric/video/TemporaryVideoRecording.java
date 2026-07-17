package dev.tobyscamera.fabric.video;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;

/** Disk-backed lossless source frames, removed on cancellation or successful upload. */
public final class TemporaryVideoRecording implements AutoCloseable {
    private final Path directory;
    private int frameCount;

    private TemporaryVideoRecording(Path directory) { this.directory = directory; }

    public static TemporaryVideoRecording create(Path root) throws IOException {
        return new TemporaryVideoRecording(Files.createDirectories(root).resolve(UUID.randomUUID().toString()));
    }

    public static void cleanupAbandoned(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) if (Files.isDirectory(entry)) deleteTree(entry);
        }
    }

    /** PNG encoding happens on the video writer thread; callers retain ownership of the NativeImage. */
    public synchronized void appendNativeImage(NativeImage image) throws IOException {
        if (!Files.exists(directory)) Files.createDirectories(directory);
        image.writeToFile(framePath(frameCount));
        frameCount++;
    }

    public BufferedImage read(int index) throws IOException {
        if (index < 0 || index >= frameCount) throw new IndexOutOfBoundsException(index);
        BufferedImage image = ImageIO.read(framePath(index).toFile());
        if (image == null) throw new IOException("Could not decode recorded PNG frame " + index);
        return image;
    }

    public synchronized int frameCount() { return frameCount; }
    public Path directory() { return directory; }

    private Path framePath(int index) { return directory.resolve("frame-%06d.png".formatted(index)); }

    @Override public void close() throws IOException {
        deleteTree(directory);
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException exception) { throw new VideoCleanupException(exception); }
        }); } catch (VideoCleanupException exception) { throw exception.getCause(); }
    }

    private static final class VideoCleanupException extends RuntimeException {
        private VideoCleanupException(IOException cause) { super(cause); }
        @Override public IOException getCause() { return (IOException) super.getCause(); }
    }
}
