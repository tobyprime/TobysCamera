package dev.tobyscamera.folia.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapItemDeliveryTest {
    @Test
    void dropsEveryMapThatDoesNotFitInTheInventory() {
        List<Integer> printedMaps = java.util.stream.IntStream.range(0, 144).boxed().toList();
        List<Integer> inventory = new ArrayList<>();
        List<Integer> dropped = new ArrayList<>();

        MapItemDelivery.deliver(printedMaps, map -> {
            if (inventory.size() < 36) {
                inventory.add(map);
                return Map.of();
            }
            return Map.of(0, map);
        }, dropped::add);

        assertEquals(36, inventory.size());
        assertEquals(108, dropped.size());
        assertEquals(144, inventory.size() + dropped.size());
    }
}
