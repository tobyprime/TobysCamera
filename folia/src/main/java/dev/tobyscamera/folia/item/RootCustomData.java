package dev.tobyscamera.folia.item;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/** Reads and writes the root minecraft:custom_data Component, not Bukkit PDC. */
public final class RootCustomData {
    private RootCustomData() { }

    public static boolean contains(ItemStack item, NamespacedKey key) { return tag(item).contains(key.toString()); }
    public static int intOr(ItemStack item, NamespacedKey key, int fallback) { return tag(item).getIntOr(key.toString(), fallback); }

    public static void update(ItemStack item, Consumer<CompoundTag> editor) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.unwrap(item);
        CustomData.update(DataComponents.CUSTOM_DATA, nms, editor);
    }

    public static CompoundTag tag(ItemStack item) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.unwrap(item);
        CustomData data = nms.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
