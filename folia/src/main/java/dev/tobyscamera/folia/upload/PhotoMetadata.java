package dev.tobyscamera.folia.upload;

import org.bukkit.entity.Player;

/** Ephemeral metadata associated with one accepted upload token. */
public record PhotoMetadata(String photographer, String world, int x, int y, int z) {
    public static PhotoMetadata capture(Player player) {
        var location = player.getLocation();
        return new PhotoMetadata(player.getName(), player.getWorld().getKey().asString(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String coordinates() { return world + " " + x + ", " + y + ", " + z; }
}
