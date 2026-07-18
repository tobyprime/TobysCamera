package dev.tobyscamera.fabric;

import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

final class ClientPlatform {
    private ClientPlatform() {
    }

    static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
        return KeyBindingHelper.registerKeyBinding(keyMapping);
    }

    static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPayload.TYPE, CameraPayload.CODEC);
    }

    static void registerViewfinderHud(ViewfinderOverlay overlay) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("tobyscamera", "viewfinder"), (graphics, delta) -> overlay.render(graphics));
    }
}
