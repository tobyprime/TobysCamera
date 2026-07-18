package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

/** Immutable persistent identity for a camera map that is currently in use. */
public sealed interface MediaMapDescriptor permits MediaMapDescriptor.PhotoTile, MediaMapDescriptor.VideoTile,
        MediaMapDescriptor.PhotoBagPreview, MediaMapDescriptor.VideoBagPreview {
    int mapId();
    UUID mediaId();
    Type type();

    enum Type {
        PHOTO_TILE,
        VIDEO_TILE,
        PHOTO_BAG_PREVIEW,
        VIDEO_BAG_PREVIEW
    }

    record PhotoTile(int mapId, UUID mediaId, TileCoordinate coordinate) implements MediaMapDescriptor {
        @Override public Type type() { return Type.PHOTO_TILE; }
    }

    record VideoTile(int mapId, UUID mediaId, TileCoordinate coordinate) implements MediaMapDescriptor {
        @Override public Type type() { return Type.VIDEO_TILE; }
    }

    record PhotoBagPreview(int mapId, UUID mediaId) implements MediaMapDescriptor {
        @Override public Type type() { return Type.PHOTO_BAG_PREVIEW; }
    }

    record VideoBagPreview(int mapId, UUID mediaId) implements MediaMapDescriptor {
        @Override public Type type() { return Type.VIDEO_BAG_PREVIEW; }
    }

    static Optional<MediaMapDescriptor> from(ItemStack item) {
        Integer mapId = mapId(item);
        if (mapId == null) return Optional.empty();

        CompoundTag tag = RootCustomData.tag(item);
        if (tag.contains("tobyscamera:photo_bag")) return bagPreview(mapId, tag);
        if (tag.contains("tobyscamera:photo_id")) return tile(mapId, tag, "tobyscamera:photo_id", true);
        if (tag.contains("tobyscamera:video_id")) return tile(mapId, tag, "tobyscamera:video_id", false);
        return Optional.empty();
    }

    private static Integer mapId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)
                || !meta.hasMapView()) return null;
        MapView map = meta.getMapView();
        return map == null ? null : map.getId();
    }

    private static Optional<MediaMapDescriptor> tile(int mapId, CompoundTag tag, String mediaIdKey, boolean photo) {
        UUID mediaId = uuid(tag, mediaIdKey);
        if (mediaId == null) return Optional.empty();
        int x = tag.getIntOr("tobyscamera:tile_x", -1);
        int y = tag.getIntOr("tobyscamera:tile_y", -1);
        if (x < 0 || y < 0) return Optional.empty();
        TileCoordinate coordinate = new TileCoordinate(x, y);
        return Optional.of(photo ? new PhotoTile(mapId, mediaId, coordinate) : new VideoTile(mapId, mediaId, coordinate));
    }

    private static Optional<MediaMapDescriptor> bagPreview(int mapId, CompoundTag tag) {
        UUID mediaId = uuid(tag, "tobyscamera:media_id");
        if (mediaId == null) return Optional.empty();
        return switch (tag.getString("tobyscamera:bag_kind").orElse("")) {
            case "PHOTO" -> Optional.of(new PhotoBagPreview(mapId, mediaId));
            case "VIDEO" -> Optional.of(new VideoBagPreview(mapId, mediaId));
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
