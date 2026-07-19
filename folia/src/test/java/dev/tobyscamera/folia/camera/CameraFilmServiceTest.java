package dev.tobyscamera.folia.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CameraFilmServiceTest {
    @Test
    void createsFilmLoreWithRemainingFilmAndMaximumGridSize() {
        var lore = CameraFilmService.lore(9, 4).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();

        assertEquals(java.util.List.of("剩余胶卷: 9", "最大尺寸: 4x"), lore);
    }
}
