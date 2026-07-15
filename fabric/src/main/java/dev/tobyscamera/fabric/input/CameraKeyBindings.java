package dev.tobyscamera.fabric.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class CameraKeyBindings {
    private CameraKeyBindings() {
    }

    public static int defaultShutterKey() {
        return GLFW.GLFW_KEY_ENTER;
    }

    public static KeyMapping shutter() {
        return new KeyMapping("key.tobyscamera.shutter", InputConstants.Type.KEYSYM, defaultShutterKey(), CameraKeyCategory.value());
    }
}
