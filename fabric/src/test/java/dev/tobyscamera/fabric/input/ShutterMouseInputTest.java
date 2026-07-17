package dev.tobyscamera.fabric.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class ShutterMouseInputTest {
    @Test
    void consumesAMouseButtonBoundToTheShutterBeforeVanillaCanTreatItAsAttack() {
        KeyMapping shutter = new KeyMapping("test.shutter.left", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CameraKeyCategory.value());
        AtomicInteger shutterPresses = new AtomicInteger();

        boolean consumed = ShutterMouseInput.consume(shutter,
                new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)),
                () -> { shutterPresses.incrementAndGet(); return true; });

        assertTrue(consumed);
        assertEquals(1, shutterPresses.get());
    }

    @Test
    void leavesOtherMouseButtonsForTheirNormalVanillaAction() {
        KeyMapping shutter = new KeyMapping("test.shutter.left.other", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CameraKeyCategory.value());
        AtomicInteger shutterPresses = new AtomicInteger();

        boolean consumed = ShutterMouseInput.consume(shutter,
                new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)),
                () -> { shutterPresses.incrementAndGet(); return true; });

        assertFalse(consumed);
        assertEquals(0, shutterPresses.get());
    }

    @Test
    void leavesTheShutterButtonForTheCompositionControlsWhileTheyAreOpen() {
        KeyMapping shutter = new KeyMapping("test.shutter.controls", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CameraKeyCategory.value());
        AtomicInteger shutterPresses = new AtomicInteger();

        boolean consumed = ShutterMouseInput.consume(shutter,
                new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)),
                () -> false,
                () -> { shutterPresses.incrementAndGet(); return true; });

        assertFalse(consumed);
        assertEquals(0, shutterPresses.get());
    }
}
