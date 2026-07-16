package dev.tobyscamera.folia.upload;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.bukkit.entity.Player;

/** Ephemeral metadata associated with one accepted upload token. */
public record PhotoMetadata(String photographer, String world, int x, int y, int z, Instant capturedAt) {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static PhotoMetadata capture(Player player) {
        var location = player.getLocation();
        return new PhotoMetadata(player.getName(), player.getWorld().getKey().asString(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), Instant.now());
    }

    public String coordinates() { return world + " " + x + ", " + y + ", " + z; }
    public String capturedTime() { return TIME_FORMAT.format(capturedAt); }
}
