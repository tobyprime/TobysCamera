package dev.tobyscamera.folia.map;

import java.util.function.IntSupplier;

/**
 * Reserves map IDs from vanilla's own monotonically increasing allocator without
 * creating any {@code MapItemSavedData}.  Because every virtual ID is consumed
 * from that allocator, a later vanilla map allocation cannot collide with it.
 */
public final class VirtualMapIdAllocator {
    private final IntSupplier nextVanillaId;
    private int lastAllocated = -1;

    public VirtualMapIdAllocator(IntSupplier nextVanillaId) {
        this.nextVanillaId = nextVanillaId;
    }

    public synchronized int allocate() {
        int id = nextVanillaId.getAsInt();
        if (id < 0 || id <= lastAllocated) {
            throw new IllegalStateException("vanilla map ID allocator did not advance");
        }
        lastAllocated = id;
        return id;
    }
}
