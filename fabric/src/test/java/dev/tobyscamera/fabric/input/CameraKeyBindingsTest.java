package dev.tobyscamera.fabric.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class CameraKeyBindingsTest {
    @Test
    void usesLeftMouseAsTheDefaultShutterButton() {
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_LEFT, CameraKeyBindings.defaultShutterKey());
        assertEquals(InputConstants.Type.MOUSE, CameraKeyBindings.shutter().getDefaultKey().getType());
    }

    @Test
    void usesMinusAndEqualsAsTheDefaultZoomKeys() {
        assertEquals(GLFW.GLFW_KEY_MINUS, CameraKeyBindings.defaultZoomOutKey());
        assertEquals(GLFW.GLFW_KEY_EQUAL, CameraKeyBindings.defaultZoomInKey());
    }

    @Test
    void usesOneDefaultKeyToCycleVideoFps() {
        assertEquals(GLFW.GLFW_KEY_RIGHT_BRACKET, CameraKeyBindings.defaultVideoFpsKey());
    }
}
