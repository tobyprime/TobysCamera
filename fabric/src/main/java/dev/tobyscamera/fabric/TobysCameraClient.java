package dev.tobyscamera.fabric;

import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.fabric.camera.PhotoUploadController;
import dev.tobyscamera.fabric.camera.HeldCameraChecker;
import java.awt.image.BufferedImage;
import dev.tobyscamera.fabric.net.CameraPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class TobysCameraClient implements ClientModInitializer {
    private static final PhotoUploadController UPLOADS = new PhotoUploadController();
    private static final KeyMapping CAPTURE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.tobyscamera.capture", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
                    KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tobyscamera", "camera"))));
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPayload.TYPE, CameraPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE, (payload, context) -> {
            try {
                UPLOADS.handleServerPacket(PacketCodec.decode(payload.data()));
                if (UPLOADS.awaitingPreview()) Screenshot.takeScreenshot(context.client().getMainRenderTarget(), nativeImage -> {
                    BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, nativeImage.getPixel(x, y));
                    nativeImage.close();
                    UPLOADS.setPreview(image);
                });
            } catch (RuntimeException ignored) {
                // Server messages are untrusted until the shared codec accepts them.
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CAPTURE_KEY.consumeClick()) {
                if (client.player == null) return;
                if (UPLOADS.awaitingPreview()) { UPLOADS.confirmPreview(); continue; }
                if (HeldCameraChecker.isCamera(client.player.getMainHandItem()) || HeldCameraChecker.isCamera(client.player.getOffhandItem())) UPLOADS.requestCapture();
            }
        });
    }
}
