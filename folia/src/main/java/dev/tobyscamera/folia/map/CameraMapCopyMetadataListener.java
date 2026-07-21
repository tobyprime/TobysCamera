package dev.tobyscamera.folia.map;

import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagFactory;
import dev.tobyscamera.folia.item.RootCustomData;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/** Retains camera-map metadata and makes one bag copy cost its complete tile grid. */
public final class CameraMapCopyMetadataListener implements Listener {
    private static final NamespacedKey PHOTO_ID = new NamespacedKey("tobyscamera", "photo_id");

    @EventHandler
    public void onPrepareCopy(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.FILLED_MAP) return;
        ItemStack source = source(event.getInventory().getMatrix());
        if (source == null) return;
        if (PhotoBagFactory.isCopy(source)) {
            event.getInventory().setResult(null);
            return;
        }
        if (!PhotoBagFactory.isBag(source)) {
            ItemStack copy = PhotoBagFactory.markCopy(source);
            copy.setAmount(result.getAmount());
            event.getInventory().setResult(copy);
            return;
        }
        int required = requiredBlankMaps(source);
        if (countBlankMaps(event.getInventory().getMatrix()) < required) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(singleCopy(source));
    }

    /** Cartography tables have no vanilla recipe for bags that cost more than one blank map. */
    @EventHandler
    public void onPrepareCartographyCopy(PrepareInventoryResultEvent event) {
        if (!(event.getInventory() instanceof CartographyInventory inventory)) return;
        ItemStack source = inventory.getItem(0);
        if (!isCameraMap(source)) return;
        if (PhotoBagFactory.isCopy(source)) {
            event.setResult(null);
            return;
        }
        if (!PhotoBagFactory.isBag(source)) {
            event.setResult(PhotoBagFactory.markCopy(source));
            return;
        }
        ItemStack blanks = inventory.getItem(1);
        if (blanks == null || blanks.getType() != Material.MAP || blanks.getAmount() < requiredBlankMaps(source)) {
            event.setResult(null);
            return;
        }
        event.setResult(singleCopy(source));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTakeCraftingBagCopy(CraftItemEvent event) {
        ItemStack source = source(event.getInventory().getMatrix());
        if (!PhotoBagFactory.isBag(source)) return;
        if (PhotoBagFactory.isCopy(source)) {
            event.setCancelled(true);
            return;
        }
        int required = requiredBlankMaps(source);
        List<Integer> blankSlots = blankSlots(event.getInventory().getMatrix());
        if (countBlankMaps(event.getInventory().getMatrix()) < required || !giveCopy(event, singleCopy(source))) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        consume(event.getInventory(), blankSlots, required);
        event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTakeCartographyBagCopy(io.papermc.paper.event.player.CartographyItemEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        CartographyInventory inventory = event.getInventory();
        ItemStack source = inventory.getItem(0);
        ItemStack blanks = inventory.getItem(1);
        if (isCameraMap(source) && PhotoBagFactory.isCopy(source)) {
            event.setCancelled(true);
            return;
        }
        if (!PhotoBagFactory.isBag(source)) return;
        int required = requiredBlankMaps(source);
        if (blanks == null || blanks.getType() != Material.MAP || blanks.getAmount() < required || !giveCopy(event, singleCopy(source))) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        setAmount(inventory, 1, blanks.getAmount() - required);
        inventory.setResult(null);
    }

    private static ItemStack source(ItemStack[] matrix) {
        ItemStack source = null;
        for (ItemStack item : matrix) {
            if (item == null || item.isEmpty()) continue;
            if (item.getType() == Material.FILLED_MAP && isCameraMap(item)) source = item;
        }
        return source;
    }

    private static int requiredBlankMaps(ItemStack source) {
        try {
            PhotoBagData bag = PhotoBagFactory.read(source);
            return PhotoBagCopyCost.requiredBlankMaps(bag);
        } catch (IllegalArgumentException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static ItemStack singleCopy(ItemStack source) {
        ItemStack copy = PhotoBagFactory.copyForPrint(source);
        copy.setAmount(1);
        return copy;
    }

    private static int countBlankMaps(ItemStack[] matrix) {
        int count = 0;
        for (ItemStack item : matrix) if (item != null && item.getType() == Material.MAP) count += item.getAmount();
        return count;
    }

    private static List<Integer> blankSlots(ItemStack[] matrix) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < matrix.length; slot++) if (matrix[slot] != null && matrix[slot].getType() == Material.MAP) slots.add(slot);
        return slots;
    }

    /**
     * CraftingInventory slot zero is the result; matrix indices start at its first input slot.
     * Replacing the complete matrix avoids accidentally writing a matrix index into the result-inclusive inventory.
     */
    private static void consume(CraftingInventory inventory, List<Integer> slots, int required) {
        ItemStack[] matrix = inventory.getMatrix();
        int[] amounts = slots.stream().mapToInt(slot -> matrix[slot].getAmount()).toArray();
        int[] remaining = PhotoBagCopyCost.consumeBlankMaps(required, amounts);
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (remaining[index] <= 0) matrix[slot] = null;
            else matrix[slot].setAmount(remaining[index]);
        }
        inventory.setMatrix(matrix);
    }

    private static void setAmount(org.bukkit.inventory.Inventory inventory, int slot, int amount) {
        if (amount <= 0) inventory.setItem(slot, null);
        else {
            ItemStack item = inventory.getItem(slot);
            item.setAmount(amount);
            inventory.setItem(slot, item);
        }
    }

    private static boolean giveCopy(InventoryClickEvent event, ItemStack copy) {
        if (event.isShiftClick()) return event.getWhoClicked().getInventory().addItem(copy).isEmpty();
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.isEmpty()) { event.setCursor(copy); return true; }
        if (!cursor.isSimilar(copy) || cursor.getAmount() >= cursor.getMaxStackSize()) return false;
        cursor.setAmount(cursor.getAmount() + 1);
        event.setCursor(cursor);
        return true;
    }

    private static boolean isCameraMap(ItemStack item) {
        return PhotoBagFactory.isBag(item) || RootCustomData.contains(item, PHOTO_ID);
    }
}
