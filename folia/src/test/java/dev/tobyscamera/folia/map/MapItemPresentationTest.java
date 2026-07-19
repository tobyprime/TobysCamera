package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.protocol.PhotoPresentation;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MapItemPresentationTest {
    @Test
    void usesCustomNameDescriptionAndOnlyPublicMetadata() {
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world", 1, 64, -2, Instant.parse("2026-07-17T00:00:00Z"),
                new PhotoPresentation("旅行回忆", "第一天", true, false, false));
        var text = PlainTextComponentSerializer.plainText();

        var presentation = MapItemPresentation.photo(new TileCoordinate(0, 1), metadata);

        assertEquals("旅行回忆", text.serialize(presentation.name()));
        assertEquals(List.of("网格位置: 0, 1", "第一天", "拍摄坐标: world 1, 64, -2"),
                presentation.lore().stream().map(text::serialize).toList());
    }
}
