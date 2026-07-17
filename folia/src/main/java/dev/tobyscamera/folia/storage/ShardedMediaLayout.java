package dev.tobyscamera.folia.storage;

import java.nio.file.Path;
import java.util.UUID;

/** Maps media IDs to bounded directory shards. */
final class ShardedMediaLayout {
    private ShardedMediaLayout() { }

    static Path container(Path mediaRoot, UUID id) {
        String value = id.toString();
        return mediaRoot.resolve(value.substring(0, 2)).resolve(value + ".tbc");
    }
}
