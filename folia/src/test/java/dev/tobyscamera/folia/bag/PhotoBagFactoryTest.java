package dev.tobyscamera.folia.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class PhotoBagFactoryTest {
    @Test
    void buildsPhotoBagDisplayName() {
        var text = PlainTextComponentSerializer.plainText();

        assertEquals("\u76f8\u7247\u888b", text.serialize(PhotoBagFactory.displayName(PhotoBagKind.PHOTO)));
    }

    @Test
    void hidesTechnicalIdentifiersFromPhotoBagLore() {
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world", 1, 64, -2, Instant.parse("2026-07-17T00:00:00Z"));
        PhotoBagData data = new PhotoBagData(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                PhotoBagKind.PHOTO, 42, 3, 2, metadata);
        var text = PlainTextComponentSerializer.plainText();

        assertEquals(List.of(
                "\u5c3a\u5bf8: 3\u00d72",
                "\u62cd\u6444\u8005: Toby",
                "\u62cd\u6444\u5750\u6807: world 1, 64, -2",
                "\u62cd\u6444\u65f6\u95f4: " + metadata.capturedTime(),
                "",
                "\u53f3\u952e\u957f\u6309: \u53d6\u51fa\u5730\u56fe",
                "\u53f3\u952e\u7a7a\u5c55\u793a\u6846: \u5c55\u5f00\u76f8\u7247"),
                PhotoBagFactory.lore(data).stream().map(text::serialize).toList());
    }

    @Test
    void exposesTechnicalIdentifiersOnlyInAdminDetail() {
        PhotoBagData data = new PhotoBagData(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), PhotoBagKind.PHOTO, 42, 3, 2);
        var text = PlainTextComponentSerializer.plainText();

        assertEquals(List.of("\u7c7b\u578b: \u76f8\u7247", "\u76f8\u7247ID: 123e4567-e89b-12d3-a456-426614174000", "\u9884\u89c8\u5730\u56fe: #42", "\u5c3a\u5bf8: 3\u00d72"),
                PhotoBagFactory.adminDetails(data).stream().map(text::serialize).toList());
    }
}
