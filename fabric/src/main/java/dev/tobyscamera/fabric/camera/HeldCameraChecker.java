package dev.tobyscamera.fabric.camera;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class HeldCameraChecker {
    private static final String CAMERA_KEY = "tobyscamera:camera";
    private static final String FILM_REMAINING_KEY = "tobyscamera:film_remaining";
    private static final String MAX_GRID_SIZE_KEY = "tobyscamera:max_grid_size";
    private static final String NO_FILM_REQUIRED_KEY = "tobyscamera:no_film_required";
    private HeldCameraChecker() { }

    public static boolean isCamera(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.contains(CAMERA_KEY);
    }

    /** Returns the largest square capture grid which the held camera can currently print. */
    public static int maximumGridSize(ItemStack stack) {
        if (!isCamera(stack)) return 0;
        var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        int componentMaximum = tag.getIntOr(MAX_GRID_SIZE_KEY, 4);
        if (tag.contains(NO_FILM_REQUIRED_KEY)) return Math.max(1, componentMaximum);
        int remaining = Math.max(0, tag.getIntOr(FILM_REMAINING_KEY, 0));
        return Math.min(Math.max(1, componentMaximum), (int) Math.sqrt(remaining));
    }

    public static int remainingFilm(ItemStack stack) {
        if (!isCamera(stack)) return 0;
        var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        return tag.contains(NO_FILM_REQUIRED_KEY) ? -1 : Math.max(0, tag.getIntOr(FILM_REMAINING_KEY, 0));
    }

}
