package dev.tobyscamera.folia.bag;

import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity and layout stored on a single preview-map photo bag. */
public record PhotoBagData(UUID mediaId, PhotoBagKind kind, int previewMapId, int gridWidth, int gridHeight,
        PhotoMetadata metadata) {
    public PhotoBagData(UUID mediaId, PhotoBagKind kind, int previewMapId, int gridWidth, int gridHeight) {
        this(mediaId, kind, previewMapId, gridWidth, gridHeight, null);
    }

    public PhotoBagData {
        Objects.requireNonNull(mediaId, "mediaId");
        Objects.requireNonNull(kind, "kind");
        if (previewMapId < 0) throw new IllegalArgumentException("preview map id must be non-negative");
        if (gridWidth < 1 || gridHeight < 1) throw new IllegalArgumentException("grid dimensions must be positive");
    }
}
