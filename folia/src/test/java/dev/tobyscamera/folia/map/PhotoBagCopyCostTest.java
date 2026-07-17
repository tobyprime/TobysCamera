package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoBagCopyCostTest {
    @Test
    void chargesOneBlankMapForEachPhotoBagTile() {
        PhotoBagData bag = new PhotoBagData(UUID.randomUUID(), PhotoBagKind.PHOTO, 10, 3, 2);

        assertEquals(6, PhotoBagCopyCost.requiredBlankMaps(bag));
    }

    @Test
    void consumesOnlyTheRequiredBlankMapsAcrossCraftingSlots() {
        assertArrayEquals(new int[] {0, 0, 1}, PhotoBagCopyCost.consumeBlankMaps(6, new int[] {1, 1, 5}));
    }
}
