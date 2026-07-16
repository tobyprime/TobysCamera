package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MapItemPresentationTest {
    @Test
    void presentsPhotoAndVideoMapsWithChineseNamesAndMetadata() {
        PhotoMetadata metadata = new PhotoMetadata("Toby", "world", 1, 64, -2, Instant.parse("2026-07-17T00:00:00Z"));

        var photo = MapItemPresentation.photo(new TileCoordinate(1, 2), metadata);
        var video = MapItemPresentation.video(new TileCoordinate(1, 2), metadata);
        var text = PlainTextComponentSerializer.plainText();

        assertEquals("照片", text.serialize(photo.name()));
        assertEquals("录像", text.serialize(video.name()));
        assertEquals(java.util.List.of("网格位置: 1, 2", "拍摄者: Toby", "拍摄坐标: world 1, 64, -2", "拍摄时间: " + metadata.capturedTime()),
                video.lore().stream().map(text::serialize).toList());
    }
}
