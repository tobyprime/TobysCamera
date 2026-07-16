package dev.tobyscamera.folia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void loadsDocumentedDefaults() {
        PluginSettings settings = PluginSettings.from(Map.of());

        assertEquals("tobyscamera:camera", settings.cameraTagKey());
        assertEquals(60, settings.tokenTtlSeconds());
        assertEquals(1, settings.perSecond());
        assertEquals(12, settings.perMinute());
        assertEquals(4, settings.maxGridSize());
        assertEquals(8_192, settings.chunkBytes());
        assertEquals(30, settings.uploadTimeoutSeconds());
    }

    @Test
    void permitsConfiguredGridLargerThanFour() {
        assertEquals(8, PluginSettings.from(Map.of("upload.max-grid-size", 8)).maxGridSize());
    }
}
