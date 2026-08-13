package dev.tobyscamera.fabric;

import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

final class ClientPlatform {
    private ClientPlatform() {
    }

    static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
        return KeyMappingHelper.registerKeyMapping(keyMapping);
    }

    static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CameraPayload.TYPE, CameraPayload.CODEC);
    }

    static void registerViewfinderHud(ViewfinderOverlay overlay) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("tobyscamera", "viewfinder"), (graphics, delta) -> overlay.extractRenderState(graphics));
    }
}
