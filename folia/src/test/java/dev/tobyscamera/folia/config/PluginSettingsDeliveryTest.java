package dev.tobyscamera.folia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginSettingsDeliveryTest {
    @Test
    void suppliesVirtualMapDeliveryDefaults() {
        PluginSettings settings = PluginSettings.from(Map.of());

        assertEquals(12, settings.virtualMapMaxConcurrentReads());
        assertEquals(4, settings.virtualMapPerPlayerMapsPerTick());
        assertEquals(65_536L, settings.virtualMapPerPlayerBytesPerTick());
        assertEquals(2_097_152L, settings.virtualMapGlobalBytesPerTick());
    }

    @Test
    void rejectsAPlayerBudgetThatCannotSendOneMapTile() {
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(Map.of(
                "virtual-map-delivery.per-player-bytes-per-tick", 16_383L)));
    }
}
