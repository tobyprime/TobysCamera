package dev.tobyscamera.fabric;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LanguageResourcesTest {
    @Test
    void providesEnglishAndSimplifiedChineseCameraInterfaceTranslations() throws Exception {
        assertLanguageContains("assets/tobyscamera/lang/en_us.json", "tobyscamera.viewfinder.hint");
        assertLanguageContains("assets/tobyscamera/lang/zh_cn.json", "tobyscamera.viewfinder.hint");
    }

    private static void assertLanguageContains(String resource, String key) throws Exception {
        try (InputStream input = LanguageResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, () -> "missing language resource " + resource);
            assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains('"' + key + '"'));
        }
    }
}
