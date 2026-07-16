package dev.tobyscamera.folia.camera;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import dev.tobyscamera.folia.item.RootCustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
        this.configuredMaximum = Math.max(1, configuredMaximum);
    }

    public boolean isCamera(ItemStack item) { return !item.isEmpty() && (RootCustomData.contains(item, cameraKey) || item.getPersistentDataContainer().has(cameraKey)); }
    public boolean isFilm(ItemStack item) { return !item.isEmpty() && (RootCustomData.contains(item, filmKey) || item.getPersistentDataContainer().has(filmKey)); }
    public ItemStack heldCamera(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return isCamera(mainHand) ? mainHand : isCamera(player.getInventory().getItemInOffHand())
                ? player.getInventory().getItemInOffHand() : null;
    }
    public int remaining(ItemStack camera) { return readInt(camera, remainingKey, 0); }
    public int maximum(ItemStack camera, int configuredMaximum) {
        int componentMaximum = readInt(camera, maximumKey, configuredMaximum);
        return Math.clamp(componentMaximum, 1, configuredMaximum);
    }
    public int maximumForFilm(ItemStack camera, int configuredMaximum) {
        return Math.min(maximum(camera, configuredMaximum), (int) Math.sqrt(remaining(camera)));
    }
    public void load(ItemStack camera, int filmCount) {
        if (filmCount < 1) return;
        int loaded = Math.addExact(remaining(camera), filmCount);
        int effectiveMaximum = maximum(camera, configuredMaximum);
        RootCustomData.update(camera, tag -> {
            tag.putBoolean(cameraKey.toString(), true);
            tag.putInt(remainingKey.toString(), loaded);
            tag.putInt(maximumKey.toString(), effectiveMaximum);
        });
        clearLegacy(camera);
        updateLore(camera, loaded);
    }
    public boolean consume(ItemStack camera, int maps) {
        int remaining = remaining(camera);
        if (maps < 1 || remaining < maps) return false;
        RootCustomData.update(camera, tag -> {
            tag.putBoolean(cameraKey.toString(), true);
            tag.putInt(remainingKey.toString(), remaining - maps);
        });
        clearLegacy(camera);
        updateLore(camera, remaining - maps);
        return true;
    }

    public static List<Component> lore(int remaining, int maximum) {
        return List.of(
                Component.text("剩余胶卷: " + remaining, NamedTextColor.GRAY),
                Component.text("最大尺寸: " + maximum + "x", NamedTextColor.GRAY));
    }

    private void updateLore(ItemStack camera, int remaining) {
        ItemMeta meta = camera.getItemMeta();
        List<Component> lines = new ArrayList<>();
        if (meta.lore() != null) for (Component line : meta.lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (!plain.startsWith("剩余胶卷: ") && !plain.startsWith("最大尺寸: ")) lines.add(line);
        }
        lines.addAll(lore(remaining, maximum(camera, configuredMaximum)));
        meta.lore(lines);
        camera.setItemMeta(meta);
    }

    private int readInt(ItemStack item, NamespacedKey key, int fallback) {
        return RootCustomData.contains(item, key) ? RootCustomData.intOr(item, key, fallback)
                : item.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    private void clearLegacy(ItemStack camera) {
        camera.editPersistentDataContainer(container -> {
            container.remove(cameraKey); container.remove(filmKey);
            container.remove(remainingKey); container.remove(maximumKey);
        });
    }
}
