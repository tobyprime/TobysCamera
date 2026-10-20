package dev.tobyscamera.fabric.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class CameraKeyBindings {
    private CameraKeyBindings() {
    }

    public static int defaultShutterKey() {
        return InputConstants.MOUSE_BUTTON_LEFT;
    }

    public static int defaultZoomOutKey() {
        return InputConstants.KEY_MINUS;
    }

    public static int defaultZoomInKey() {
        return InputConstants.KEY_EQUALS;
    }

    public static KeyMapping shutter() {
        return new KeyMapping("key.tobyscamera.shutter", InputConstants.Type.MOUSE, defaultShutterKey(), CameraKeyCategory.value());
    }
}
