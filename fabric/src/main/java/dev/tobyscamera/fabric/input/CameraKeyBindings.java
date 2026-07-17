package dev.tobyscamera.fabric.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class CameraKeyBindings {
    private CameraKeyBindings() {
    }

    public static int defaultShutterKey() {
        return GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    public static int defaultZoomOutKey() {
        return GLFW.GLFW_KEY_MINUS;
    }

    public static int defaultZoomInKey() {
        return GLFW.GLFW_KEY_EQUAL;
    }

    public static int defaultVideoFpsKey() {
        return GLFW.GLFW_KEY_RIGHT_BRACKET;
    }

    public static KeyMapping shutter() {
        return new KeyMapping("key.tobyscamera.shutter", InputConstants.Type.MOUSE, defaultShutterKey(), CameraKeyCategory.value());
    }
}
