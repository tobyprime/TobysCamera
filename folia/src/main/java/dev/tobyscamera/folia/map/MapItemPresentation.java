package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** User-visible names and metadata shared by original and copied camera maps. */
public final class MapItemPresentation {
    private MapItemPresentation() { }

    public static Presentation photo(TileCoordinate coordinate, PhotoMetadata metadata) { return presentation("照片", coordinate, metadata); }

    private static Presentation presentation(String name, TileCoordinate coordinate, PhotoMetadata metadata) {
        String displayName = metadata != null && !metadata.presentation().name().isEmpty() ? metadata.presentation().name() : name;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("网格位置: " + coordinate.x() + ", " + coordinate.y(), NamedTextColor.GRAY));
        if (metadata != null) {
            if (!metadata.presentation().description().isEmpty()) lore.add(Component.text(metadata.presentation().description(), NamedTextColor.GRAY));
            if (metadata.presentation().publicPhotographer()) lore.add(Component.text("拍摄者: " + metadata.photographer(), NamedTextColor.GRAY));
            if (metadata.presentation().publicAddress()) lore.add(Component.text("拍摄坐标: " + metadata.coordinates(), NamedTextColor.GRAY));
            lore.add(Component.text("拍摄时间: " + metadata.capturedTime(), NamedTextColor.GRAY));
        }
        return new Presentation(Component.text(displayName), List.copyOf(lore));
    }

    public record Presentation(Component name, List<Component> lore) { }
}
