package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

/** Immutable persistent identity for a camera map that is currently in use. */
public sealed interface MediaMapDescriptor permits MediaMapDescriptor.PhotoTile, MediaMapDescriptor.PhotoBagPreview {
    int mapId();
    UUID mediaId();
    Type type();

    enum Type {
        PHOTO_TILE,
        PHOTO_BAG_PREVIEW
    }

    record PhotoTile(int mapId, UUID mediaId, TileCoordinate coordinate) implements MediaMapDescriptor {
        @Override public Type type() { return Type.PHOTO_TILE; }
    }

    record PhotoBagPreview(int mapId, UUID mediaId) implements MediaMapDescriptor {
        @Override public Type type() { return Type.PHOTO_BAG_PREVIEW; }
    }

    static Optional<MediaMapDescriptor> from(ItemStack item) {
        Integer mapId = mapId(item);
        if (mapId == null) return Optional.empty();
        return from(mapId, RootCustomData.tag(item));
    }

    static Optional<MediaMapDescriptor> from(int mapId, CompoundTag tag) {
        if (tag.contains("tobyscamera:photo_bag")) return bagPreview(mapId, tag);
        if (tag.contains("tobyscamera:photo_id")) return tile(mapId, tag, "tobyscamera:photo_id");
        return Optional.empty();
    }

    private static Integer mapId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)
                || !meta.hasMapId()) return null;
        return meta.getMapId();
    }

    private static Optional<MediaMapDescriptor> tile(int mapId, CompoundTag tag, String mediaIdKey) {
        UUID mediaId = uuid(tag, mediaIdKey);
        if (mediaId == null) return Optional.empty();
        int x = tag.getIntOr("tobyscamera:tile_x", -1);
        int y = tag.getIntOr("tobyscamera:tile_y", -1);
        if (x < 0 || y < 0) return Optional.empty();
        TileCoordinate coordinate = new TileCoordinate(x, y);
        return Optional.of(new PhotoTile(mapId, mediaId, coordinate));
    }

    private static Optional<MediaMapDescriptor> bagPreview(int mapId, CompoundTag tag) {
        UUID mediaId = uuid(tag, "tobyscamera:media_id");
        if (mediaId == null) return Optional.empty();
        return switch (tag.getString("tobyscamera:bag_kind").orElse("")) {
            case "PHOTO" -> Optional.of(new PhotoBagPreview(mapId, mediaId));
            default -> Optional.empty();
        };
    }

    private static UUID uuid(CompoundTag tag, String key) {
        try {
            return UUID.fromString(tag.getString(key).orElse(""));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
