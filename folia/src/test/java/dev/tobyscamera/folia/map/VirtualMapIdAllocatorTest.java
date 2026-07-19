package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VirtualMapIdAllocatorTest {
    @Test
    void consumesVanillaIdsInStrictAscendingOrder() {
        AtomicInteger vanillaCounter = new AtomicInteger(41);
        VirtualMapIdAllocator allocator = new VirtualMapIdAllocator(vanillaCounter::getAndIncrement);

        assertEquals(41, allocator.allocate());
        assertEquals(42, allocator.allocate());
        assertEquals(43, allocator.allocate());
    }

    @Test
    void rejectsARepeatedVanillaIdBeforeItCanCollide() {
        VirtualMapIdAllocator allocator = new VirtualMapIdAllocator(() -> 9);

        assertEquals(9, allocator.allocate());
        assertThrows(IllegalStateException.class, allocator::allocate);
    }
}
