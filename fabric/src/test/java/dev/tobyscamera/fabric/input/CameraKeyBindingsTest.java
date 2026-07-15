package dev.tobyscamera.fabric.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class CameraKeyBindingsTest {
    @Test
    void usesEnterAsTheDefaultShutterKey() {
        assertEquals(GLFW.GLFW_KEY_ENTER, CameraKeyBindings.defaultShutterKey());
    }
}
