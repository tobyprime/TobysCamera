package dev.tobyscamera.fabric.camera;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class HeldCameraChecker {
    private HeldCameraChecker() { }

    public static boolean isCamera(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("tobyscamera:camera");
    }
}
