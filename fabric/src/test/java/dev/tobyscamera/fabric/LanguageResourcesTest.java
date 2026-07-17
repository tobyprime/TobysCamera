package dev.tobyscamera.fabric;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void labelsPhotoPreviewActionsForPrintingAndResolution() throws Exception {
        assertLanguageContainsText("assets/tobyscamera/lang/en_us.json", "\"tobyscamera.preview.use_photo\": \"Print photo\"");
        assertLanguageContainsText("assets/tobyscamera/lang/en_us.json", "\"tobyscamera.preview.resolution\": \"Resolution %sx\"");
        assertLanguageContainsText("assets/tobyscamera/lang/zh_cn.json", "\"tobyscamera.preview.use_photo\": \"\\u6253\\u5370\\u7167\\u7247\"");
        assertLanguageContainsText("assets/tobyscamera/lang/zh_cn.json", "\"tobyscamera.preview.resolution\": \"\\u5206\\u8fa8\\u7387 %sx\"");
    }

    @Test
    void viewfinderHintShowsRightClickCloseWithoutEsc() throws Exception {
        assertLanguageContainsText("assets/tobyscamera/lang/en_us.json", "[Right Mouse] close");
        assertLanguageContainsText("assets/tobyscamera/lang/zh_cn.json", "[\\u53f3\\u952e] \\u5173\\u95ed");
        assertLanguageDoesNotContainText("assets/tobyscamera/lang/en_us.json", "[Esc] close");
        assertLanguageDoesNotContainText("assets/tobyscamera/lang/zh_cn.json", "[Esc]");
    }

    private static void assertLanguageContains(String resource, String key) throws Exception {
        assertLanguageContainsText(resource, '"' + key + '"');
    }

    private static void assertLanguageContainsText(String resource, String text) throws Exception {
        assertTrue(readResource(resource).contains(text));
    }

    private static void assertLanguageDoesNotContainText(String resource, String text) throws Exception {
        assertFalse(readResource(resource).contains(text));
    }

    private static String readResource(String resource) throws Exception {
        try (InputStream input = LanguageResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, () -> "missing language resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
