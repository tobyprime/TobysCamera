package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.bag.PhotoBagData;

/** The material cost of duplicating a preview bag equals its printed map-tile count. */
final class PhotoBagCopyCost {
    private PhotoBagCopyCost() { }

    static int requiredBlankMaps(PhotoBagData bag) {
        return Math.multiplyExact(bag.gridWidth(), bag.gridHeight());
    }

    /** Returns the remaining item count in each input slot after one copy is paid for. */
    static int[] consumeBlankMaps(int required, int[] quantities) {
        int[] remaining = quantities.clone();
        int left = required;
        for (int index = 0; index < remaining.length && left > 0; index++) {
            int consumed = Math.min(left, remaining[index]);
            remaining[index] -= consumed;
            left -= consumed;
        }
        if (left != 0) throw new IllegalArgumentException("not enough blank maps");
        return remaining;
    }
}
