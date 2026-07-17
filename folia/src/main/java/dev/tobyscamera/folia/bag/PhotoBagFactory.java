package dev.tobyscamera.folia.bag;

import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.map.TileMapRenderer;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/** Manufactures preview-map photo bags and restores their immutable custom-data identity. */
public final class PhotoBagFactory {
    private static final NamespacedKey BAG = key("photo_bag");
    private static final NamespacedKey KIND = key("bag_kind");
    private static final NamespacedKey MEDIA_ID = key("media_id");
    private static final NamespacedKey PREVIEW_MAP_ID = key("preview_map_id");
    private static final NamespacedKey GRID_WIDTH = key("grid_width");
    private static final NamespacedKey GRID_HEIGHT = key("grid_height");
    private static final NamespacedKey PLACEMENT_ID = key("bag_placement_id");
    private static final NamespacedKey TILE_X = key("bag_tile_x");
    private static final NamespacedKey TILE_Y = key("bag_tile_y");
    private static final NamespacedKey PHOTOGRAPHER = key("photographer");
    private static final NamespacedKey CAPTURE_WORLD = key("capture_world");
    private static final NamespacedKey CAPTURE_X = key("capture_x");
    private static final NamespacedKey CAPTURE_Y = key("capture_y");
    private static final NamespacedKey CAPTURE_Z = key("capture_z");
    private static final NamespacedKey CAPTURED_AT = key("captured_at");

    private PhotoBagFactory() { }

    public static ItemStack create(World world, UUID mediaId, PhotoBagKind kind, int width, int height, byte[] previewPixels) {
        return create(world, mediaId, kind, width, height, null, previewPixels);
    }

    public static ItemStack create(World world, UUID mediaId, PhotoBagKind kind, int width, int height,
            PhotoMetadata metadata, byte[] previewPixels) {
        if (previewPixels.length != 16_384) throw new IllegalArgumentException("preview must contain 16384 palette pixels");
        MapView preview = Bukkit.createMap(world);
        preview.setTrackingPosition(false);
        preview.setUnlimitedTracking(false);
        preview.setLocked(true);
        for (MapRenderer renderer : preview.getRenderers()) preview.removeRenderer(renderer);
        preview.addRenderer(new TileMapRenderer(previewPixels));
        return create(new PhotoBagData(mediaId, kind, preview.getId(), width, height, metadata));
    }

    public static ItemStack create(PhotoBagData data) {
        MapView preview = Bukkit.getMap(data.previewMapId());
        if (preview == null) throw new IllegalArgumentException("preview map no longer exists: " + data.previewMapId());
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(preview);
        meta.displayName(displayName(data.kind()));
        meta.lore(lore(data));
        item.setItemMeta(meta);
        RootCustomData.update(item, tag -> {
            tag.putBoolean(BAG.toString(), true);
            tag.putString(KIND.toString(), data.kind().name());
            tag.putString(MEDIA_ID.toString(), data.mediaId().toString());
            tag.putInt(PREVIEW_MAP_ID.toString(), data.previewMapId());
            tag.putInt(GRID_WIDTH.toString(), data.gridWidth());
            tag.putInt(GRID_HEIGHT.toString(), data.gridHeight());
            writeMetadata(tag, data.metadata());
        });
        return item;
    }

    static Component displayName(PhotoBagKind kind) {
        return Component.text(typeName(kind) + "\u888b");
    }

    public static List<Component> lore(PhotoBagData data) {
        String type = typeName(data.kind());
        String idLabel = data.kind() == PhotoBagKind.PHOTO ? "\u76f8\u7247ID" : "\u5f55\u50cfID";
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("\u7c7b\u578b: " + type, NamedTextColor.GRAY));
        lore.add(Component.text("\u5c3a\u5bf8: " + data.gridWidth() + "\u00d7" + data.gridHeight(), NamedTextColor.GRAY));
        lore.add(Component.text(idLabel + ": " + shortId(data.mediaId()), NamedTextColor.GRAY));
        lore.add(Component.text("\u9884\u89c8\u5730\u56fe: #" + data.previewMapId(), NamedTextColor.GRAY));
        PhotoMetadata metadata = data.metadata();
        if (metadata != null) {
            lore.add(Component.text("\u62cd\u6444\u8005: " + metadata.photographer(), NamedTextColor.GRAY));
            lore.add(Component.text("\u62cd\u6444\u5750\u6807: " + metadata.coordinates(), NamedTextColor.GRAY));
            lore.add(Component.text("\u62cd\u6444\u65f6\u95f4: " + metadata.capturedTime(), NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("\u53f3\u952e\u957f\u6309: \u53d6\u51fa\u5730\u56fe", NamedTextColor.YELLOW));
        lore.add(Component.text("\u53f3\u952e\u7a7a\u5c55\u793a\u6846: \u5c55\u5f00" + type, NamedTextColor.YELLOW));
        return List.copyOf(lore);
    }

    private static String typeName(PhotoBagKind kind) {
        return kind == PhotoBagKind.PHOTO ? "\u76f8\u7247" : "\u5f55\u50cf";
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** Installs the non-persistent renderer of an existing preview map after a restart. */
    public static void restorePreview(MapView preview, byte[] previewPixels) {
        if (previewPixels.length != 16_384) throw new IllegalArgumentException("preview must contain 16384 palette pixels");
        for (MapRenderer renderer : preview.getRenderers()) preview.removeRenderer(renderer);
        preview.addRenderer(new TileMapRenderer(previewPixels));
    }

    public static boolean isBag(ItemStack item) { return item != null && item.getType() == Material.FILLED_MAP && RootCustomData.contains(item, BAG); }

    public static PhotoBagData read(ItemStack item) {
        if (!isBag(item)) throw new IllegalArgumentException("item is not a photo bag");
        try {
            return new PhotoBagData(UUID.fromString(RootCustomData.stringOr(item, MEDIA_ID, "")),
                    PhotoBagKind.valueOf(RootCustomData.stringOr(item, KIND, "")),
                    RootCustomData.intOr(item, PREVIEW_MAP_ID, -1), RootCustomData.intOr(item, GRID_WIDTH, 0),
                    RootCustomData.intOr(item, GRID_HEIGHT, 0), readMetadata(item));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("photo bag data is invalid", exception);
        }
    }

    public static ItemStack markPlaced(ItemStack map, PhotoBagData bag, UUID placementId, int tileX, int tileY) {
        ItemStack marked = map.clone();
        RootCustomData.update(marked, tag -> {
            tag.putString(PLACEMENT_ID.toString(), placementId.toString());
            tag.putString(KIND.toString(), bag.kind().name());
            tag.putString(MEDIA_ID.toString(), bag.mediaId().toString());
            tag.putInt(PREVIEW_MAP_ID.toString(), bag.previewMapId());
            tag.putInt(GRID_WIDTH.toString(), bag.gridWidth());
            tag.putInt(GRID_HEIGHT.toString(), bag.gridHeight());
            tag.putInt(TILE_X.toString(), tileX);
            tag.putInt(TILE_Y.toString(), tileY);
            writeMetadata(tag, bag.metadata());
        });
        return marked;
    }

    public static PlacedMember readPlaced(ItemStack item) {
        if (item == null || !RootCustomData.contains(item, PLACEMENT_ID)) return null;
        try {
            return new PlacedMember(UUID.fromString(RootCustomData.stringOr(item, PLACEMENT_ID, "")), readPlacedBag(item),
                    RootCustomData.intOr(item, TILE_X, -1), RootCustomData.intOr(item, TILE_Y, -1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PhotoBagData readPlacedBag(ItemStack item) {
        return new PhotoBagData(UUID.fromString(RootCustomData.stringOr(item, MEDIA_ID, "")),
                PhotoBagKind.valueOf(RootCustomData.stringOr(item, KIND, "")), RootCustomData.intOr(item, PREVIEW_MAP_ID, -1),
                RootCustomData.intOr(item, GRID_WIDTH, 0), RootCustomData.intOr(item, GRID_HEIGHT, 0), readMetadata(item));
    }

    public record PlacedMember(UUID placementId, PhotoBagData bag, int tileX, int tileY) {
        public PlacedMember { if (tileX < 0 || tileY < 0) throw new IllegalArgumentException("tile coordinates must be non-negative"); }
    }

    private static NamespacedKey key(String path) { return new NamespacedKey("tobyscamera", path); }

    private static void writeMetadata(CompoundTag tag, PhotoMetadata metadata) {
        if (metadata == null) return;
        tag.putString(PHOTOGRAPHER.toString(), metadata.photographer());
        tag.putString(CAPTURE_WORLD.toString(), metadata.world());
        tag.putInt(CAPTURE_X.toString(), metadata.x());
        tag.putInt(CAPTURE_Y.toString(), metadata.y());
        tag.putInt(CAPTURE_Z.toString(), metadata.z());
        tag.putLong(CAPTURED_AT.toString(), metadata.capturedAt().toEpochMilli());
    }

    private static PhotoMetadata readMetadata(ItemStack item) {
        if (!RootCustomData.contains(item, PHOTOGRAPHER)) return null;
        return new PhotoMetadata(RootCustomData.stringOr(item, PHOTOGRAPHER, ""),
                RootCustomData.stringOr(item, CAPTURE_WORLD, ""),
                RootCustomData.intOr(item, CAPTURE_X, 0),
                RootCustomData.intOr(item, CAPTURE_Y, 0),
                RootCustomData.intOr(item, CAPTURE_Z, 0),
                Instant.ofEpochMilli(RootCustomData.longOr(item, CAPTURED_AT, 0L)));
    }
}
