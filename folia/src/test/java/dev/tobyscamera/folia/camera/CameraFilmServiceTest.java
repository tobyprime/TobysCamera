package dev.tobyscamera.folia.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class CameraFilmServiceTest {
    @Test
    void createsFilmLoreWithRemainingFilmAndMaximumGridSize() {
        var lore = CameraFilmService.lore(9, 4).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();

        assertEquals(java.util.List.of("剩余胶卷: 9", "最大尺寸: 4x"), lore);
    }

    @Test
    void capsCameraVideoFpsAtTheConfiguredServerLimit() {
        assertEquals(10, CameraFilmService.capVideoFps(17, 10));
        assertEquals(8, CameraFilmService.capVideoFps(8, 10));
        assertEquals(1, CameraFilmService.capVideoFps(0, 10));
    }

    @Test
    void capsIndependentVideoGridAndFrameComponentsAtServerLimits() {
        assertEquals(3, CameraFilmService.capVideoGridSize(3, 8));
        assertEquals(8, CameraFilmService.capVideoGridSize(12, 8));
        assertEquals(24, CameraFilmService.capVideoFrames(24, 100));
        assertEquals(100, CameraFilmService.capVideoFrames(240, 100));
    }
}
