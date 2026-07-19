package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MediaMapDescriptorVirtualIdTest {
    @Test
    void readsPhotoIdentityFromAMapIdWithoutResolvingABukkitMapView() {
        UUID photoId = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("tobyscamera:photo_id", photoId.toString());
        tag.putInt("tobyscamera:tile_x", 2);
        tag.putInt("tobyscamera:tile_y", 3);

        MediaMapDescriptor.PhotoTile tile = (MediaMapDescriptor.PhotoTile) MediaMapDescriptor.from(7_001, tag).orElseThrow();
        assertEquals(7_001, tile.mapId());
        assertEquals(photoId, tile.mediaId());
    }
}
