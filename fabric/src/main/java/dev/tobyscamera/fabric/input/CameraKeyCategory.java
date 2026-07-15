package dev.tobyscamera.fabric.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class CameraKeyCategory {
    private static final KeyMapping.Category VALUE = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tobyscamera", "camera"));

    private CameraKeyCategory() {
    }

    public static KeyMapping.Category value() {
        return VALUE;
    }
}
