package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.item.RootCustomData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/** Retains camera-map identity, name and lore when vanilla's map-copy recipe creates another map. */
public final class CameraMapCopyMetadataListener implements Listener {
    private static final NamespacedKey PHOTO_ID = new NamespacedKey("tobyscamera", "photo_id");
    private static final NamespacedKey VIDEO_ID = new NamespacedKey("tobyscamera", "video_id");

    @EventHandler
    public void onPrepareCopy(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.FILLED_MAP) return;
        ItemStack source = null;
        int blankMaps = 0;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.isEmpty()) continue;
            if (item.getType() == Material.MAP) blankMaps++;
            else if (item.getType() == Material.FILLED_MAP && isCameraMap(item)) source = item;
        }
        if (source == null || blankMaps < 1) return;
        ItemStack copy = source.clone();
        copy.setAmount(result.getAmount());
        event.getInventory().setResult(copy);
    }

    private static boolean isCameraMap(ItemStack item) { return RootCustomData.contains(item, PHOTO_ID) || RootCustomData.contains(item, VIDEO_ID); }
}
