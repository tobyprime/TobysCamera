package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.storage.PhotoCoordinates;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface CompletedUploadHandler {
    void accept(Player player, UploadSession session, PhotoCoordinates coordinates);
}
