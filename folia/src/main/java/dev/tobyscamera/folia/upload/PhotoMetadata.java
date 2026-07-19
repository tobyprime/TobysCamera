package dev.tobyscamera.folia.upload;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import dev.tobyscamera.common.protocol.PhotoPresentation;
import org.bukkit.entity.Player;

/** Ephemeral metadata associated with one accepted upload token. */
public record PhotoMetadata(String photographer, String world, int x, int y, int z, Instant capturedAt, PhotoPresentation presentation) {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public PhotoMetadata(String photographer, String world, int x, int y, int z, Instant capturedAt) {
        this(photographer, world, x, y, z, capturedAt, PhotoPresentation.DEFAULT);
    }

    public static PhotoMetadata capture(Player player) {
        var location = player.getLocation();
        return new PhotoMetadata(player.getName(), player.getWorld().getKey().asString(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), Instant.now());
    }

    public PhotoMetadata withPresentation(PhotoPresentation value) {
        return new PhotoMetadata(photographer, world, x, y, z, capturedAt, value);
    }

    public String coordinates() { return world + " " + x + ", " + y + ", " + z; }
    public String capturedTime() { return TIME_FORMAT.format(capturedAt); }
}
