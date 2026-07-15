package dev.tobyscamera.folia.camera;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CameraValidator {
    boolean isHoldingCamera(Player player);
}
