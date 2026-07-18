package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.tobyscamera.folia.item.RootCustomData;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MediaMapDescriptorTest {
    private static final UUID MEDIA_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void parsesPhotoTileFromRootCustomData() {
        CompoundTag tag = tag("tobyscamera:photo_id", MEDIA_ID.toString(), 2, 3);

        assertEquals(Optional.of(new MediaMapDescriptor.PhotoTile(41, MEDIA_ID, new TileCoordinate(2, 3))),
                parse(map(41), tag));
    }

    @Test
    void parsesVideoTileFromRootCustomData() {
        CompoundTag tag = tag("tobyscamera:video_id", MEDIA_ID.toString(), 4, 5);

        assertEquals(Optional.of(new MediaMapDescriptor.VideoTile(42, MEDIA_ID, new TileCoordinate(4, 5))),
                parse(map(42), tag));
    }

    @Test
    void parsesPhotoBagPreviewFromBagIdentity() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tobyscamera:photo_bag", true);
        tag.putString("tobyscamera:bag_kind", "PHOTO");
        tag.putString("tobyscamera:media_id", MEDIA_ID.toString());

        assertEquals(Optional.of(new MediaMapDescriptor.PhotoBagPreview(43, MEDIA_ID)), parse(map(43), tag));
    }

    @Test
    void parsesVideoBagPreviewFromBagIdentity() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tobyscamera:photo_bag", true);
        tag.putString("tobyscamera:bag_kind", "VIDEO");
        tag.putString("tobyscamera:media_id", MEDIA_ID.toString());

        assertEquals(Optional.of(new MediaMapDescriptor.VideoBagPreview(47, MEDIA_ID)), parse(map(47), tag));
    }

    @Test
    void rejectsAnUntaggedMap() {
        assertEquals(Optional.empty(), parse(map(44), new CompoundTag()));
    }

    @Test
    void rejectsMalformedMediaId() {
        assertEquals(Optional.empty(), parse(map(45), tag("tobyscamera:photo_id", "not-a-uuid", 0, 0)));
    }

    @Test
    void rejectsMapsWithoutMapMetadata() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.FILLED_MAP);

        assertEquals(Optional.empty(), parse(item, tag("tobyscamera:photo_id", MEDIA_ID.toString(), 0, 0)));
    }

    @Test
    void rejectsNegativeTileCoordinates() {
        assertEquals(Optional.empty(), parse(map(46), tag("tobyscamera:video_id", MEDIA_ID.toString(), -1, 0)));
    }

    private static CompoundTag tag(String idKey, String id, int x, int y) {
        CompoundTag tag = new CompoundTag();
        tag.putString(idKey, id);
        tag.putInt("tobyscamera:tile_x", x);
        tag.putInt("tobyscamera:tile_y", y);
        return tag;
    }

    private static ItemStack map(int id) {
        ItemStack item = mock(ItemStack.class);
        MapMeta meta = mock(MapMeta.class);
        MapView view = mock(MapView.class);
        when(item.getType()).thenReturn(Material.FILLED_MAP);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasMapView()).thenReturn(true);
        when(meta.getMapView()).thenReturn(view);
        when(view.getId()).thenReturn(id);
        return item;
    }

    private static Optional<MediaMapDescriptor> parse(ItemStack item, CompoundTag tag) {
        try (MockedStatic<RootCustomData> customData = org.mockito.Mockito.mockStatic(RootCustomData.class)) {
            customData.when(() -> RootCustomData.tag(item)).thenReturn(tag);
            return MediaMapDescriptor.from(item);
        }
    }
}
