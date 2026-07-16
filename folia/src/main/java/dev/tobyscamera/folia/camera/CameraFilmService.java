package dev.tobyscamera.folia.camera;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class CameraFilmService {
    private final NamespacedKey cameraKey;
    private final NamespacedKey filmKey;
    private final NamespacedKey remainingKey;
    private final NamespacedKey maximumKey;
    private final int configuredMaximum;

    public CameraFilmService(String cameraTagKey, String filmTagKey, int configuredMaximum) {
        cameraKey = NamespacedKey.fromString(cameraTagKey);
        filmKey = NamespacedKey.fromString(filmTagKey);
        if (cameraKey == null || filmKey == null) throw new IllegalArgumentException("invalid item tag key");
        remainingKey = new NamespacedKey(cameraKey.getNamespace(), "film_remaining");
        maximumKey = new NamespacedKey(cameraKey.getNamespace(), "max_grid_size");
        this.configuredMaximum = Math.clamp(configuredMaximum, 1, 4);
    }

    public boolean isCamera(ItemStack item) { return !item.isEmpty() && item.getPersistentDataContainer().has(cameraKey); }
    public boolean isFilm(ItemStack item) { return !item.isEmpty() && item.getPersistentDataContainer().has(filmKey); }
    public ItemStack heldCamera(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return isCamera(mainHand) ? mainHand : isCamera(player.getInventory().getItemInOffHand())
                ? player.getInventory().getItemInOffHand() : null;
    }
    public int remaining(ItemStack camera) { return camera.getPersistentDataContainer().getOrDefault(remainingKey, PersistentDataType.INTEGER, 0); }
    public int maximum(ItemStack camera, int configuredMaximum) {
        int componentMaximum = camera.getPersistentDataContainer().getOrDefault(maximumKey, PersistentDataType.INTEGER, configuredMaximum);
        return Math.clamp(componentMaximum, 1, configuredMaximum);
    }
    public int maximumForFilm(ItemStack camera, int configuredMaximum) {
        return Math.min(maximum(camera, configuredMaximum), (int) Math.sqrt(remaining(camera)));
    }
    public void load(ItemStack camera, int filmCount) {
        if (filmCount < 1) return;
        int loaded = Math.addExact(remaining(camera), filmCount);
        camera.editPersistentDataContainer(container -> {
            container.set(remainingKey, PersistentDataType.INTEGER, loaded);
            if (!container.has(maximumKey)) container.set(maximumKey, PersistentDataType.INTEGER, configuredMaximum);
        });
    }
    public boolean consume(ItemStack camera, int maps) {
        int remaining = remaining(camera);
        if (maps < 1 || remaining < maps) return false;
        camera.editPersistentDataContainer(container -> container.set(remainingKey, PersistentDataType.INTEGER, remaining - maps));
        return true;
    }
}
