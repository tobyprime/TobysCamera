package dev.tobyscamera.fabric.camera;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class HeldCameraChecker {
    private HeldCameraChecker() { }

    public static boolean isCamera(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.contains("tobyscamera:camera")
                || tag.getCompoundOrEmpty("PublicBukkitValues").contains("tobyscamera:camera");
    }
}
