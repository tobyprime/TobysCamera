package dev.tobyscamera.fabric.input;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Dispatches a mouse-bound shutter before Minecraft turns that press into an attack/use action. */
public final class ShutterMouseInput {
    private ShutterMouseInput() { }

    public static boolean consume(KeyMapping shutter, MouseButtonEvent event, BooleanSupplier pressShutter) {
        return consume(shutter, event, () -> true, pressShutter);
    }

    public static boolean consume(KeyMapping shutter, MouseButtonEvent event, BooleanSupplier inputAllowed, BooleanSupplier pressShutter) {
        Objects.requireNonNull(shutter, "shutter");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(inputAllowed, "inputAllowed");
        Objects.requireNonNull(pressShutter, "pressShutter");
        return inputAllowed.getAsBoolean() && shutter.matchesMouse(event) && pressShutter.getAsBoolean();
    }

    public static boolean consumeRightClickClose(MouseButtonEvent event, BooleanSupplier closeViewfinder) {
        return consumeRightClickClose(event, () -> true, closeViewfinder);
    }

    public static boolean consumeRightClickClose(MouseButtonEvent event, BooleanSupplier inputAllowed, BooleanSupplier closeViewfinder) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(inputAllowed, "inputAllowed");
        Objects.requireNonNull(closeViewfinder, "closeViewfinder");
        return inputAllowed.getAsBoolean() && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && closeViewfinder.getAsBoolean();
    }
}
