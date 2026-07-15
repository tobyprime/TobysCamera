package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.upload.UploadSession;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface CompletedUploadHandler {
    void accept(Player player, UploadSession session);
}
