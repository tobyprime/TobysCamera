package dev.tobyscamera.folia.camera;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CameraItemValidator {
    private final NamespacedKey key;

    public CameraItemValidator(String key) {
        this.key = NamespacedKey.fromString(key);
        if (this.key == null) throw new IllegalArgumentException("invalid camera tag key: " + key);
    }

    public boolean isHoldingCamera(Player player) {
        return hasTag(player.getInventory().getItemInMainHand()) || hasTag(player.getInventory().getItemInOffHand());
    }

    private boolean hasTag(ItemStack item) {
        return !item.isEmpty() && item.getPersistentDataContainer().has(key);
    }
}
