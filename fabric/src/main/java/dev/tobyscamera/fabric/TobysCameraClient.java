package dev.tobyscamera.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.CenterSquareCropProcessor;
import dev.tobyscamera.fabric.camera.HeldCameraChecker;
import dev.tobyscamera.fabric.camera.PhotoUploadController;
import dev.tobyscamera.fabric.camera.ResizeToGridProcessor;
import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.CaptureService;
import dev.tobyscamera.fabric.viewfinder.PreviewScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSession;
import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import java.awt.image.BufferedImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class TobysCameraClient implements ClientModInitializer {
    private static final PhotoUploadController UPLOADS = new PhotoUploadController();
    private static final ViewfinderSession VIEWFINDER = new ViewfinderSession();
    private static final ViewfinderOverlay OVERLAY = new ViewfinderOverlay(VIEWFINDER);
    private static final CaptureService CAPTURE = new CaptureService();
    private static final KeyMapping VIEWFINDER_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.viewfinder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tobyscamera", "camera"))));
    private static final KeyMapping GRID_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tobyscamera", "camera"))));

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPayload.TYPE, CameraPayload.CODEC);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("tobyscamera", "viewfinder"), (graphics, delta) -> OVERLAY.render(graphics));
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE, (payload, context) -> handleServerPacket(context.client(), PacketCodec.decode(payload.data())));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void handleServerPacket(net.minecraft.client.Minecraft client, dev.tobyscamera.common.protocol.CameraPacket packet) {
        UPLOADS.handleServerPacket(packet);
        if (packet instanceof Packets.UploadGranted grant && VIEWFINDER.acceptGrant(grant.gridSize())) {
            OVERLAY.flashShutter();
            CAPTURE.requestAfterNextFrame(grant.gridSize());
        } else if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected) {
            VIEWFINDER.rejectGrant();
        }
    }

    private void tick(net.minecraft.client.Minecraft client) {
        OVERLAY.tick();
        if (client.player == null) { VIEWFINDER.close(); return; }
        boolean holdingCamera = HeldCameraChecker.isCamera(client.player.getMainHandItem()) || HeldCameraChecker.isCamera(client.player.getOffhandItem());
        if (VIEWFINDER.state() != ViewfinderState.CLOSED && !holdingCamera) VIEWFINDER.close();
        while (VIEWFINDER_KEY.consumeClick()) {
            if (VIEWFINDER.state() == ViewfinderState.CLOSED && holdingCamera) VIEWFINDER.open(); else VIEWFINDER.close();
        }
        while (GRID_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER) VIEWFINDER.cycleGrid();
        while (client.options.keyAttack.consumeClick()) {
            if (VIEWFINDER.pressShutter()) UPLOADS.requestCapture();
        }
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING && CAPTURE.tick()) {
            int gridSize = CAPTURE.takeGridSize();
            Screenshot.takeScreenshot(client.getMainRenderTarget(), nativeImage -> openPreview(client, toFrame(nativeImage, gridSize)));
        }
    }

    private void openPreview(net.minecraft.client.Minecraft client, CapturedFrame frame) {
        if (!VIEWFINDER.captureComplete()) return;
        client.setScreen(new PreviewScreen(frame,
                () -> { if (VIEWFINDER.beginUpload()) { if (UPLOADS.confirm(frame)) VIEWFINDER.finishUpload(); else VIEWFINDER.retake(); } client.setScreen(null); },
                () -> { VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private CapturedFrame toFrame(com.mojang.blaze3d.platform.NativeImage nativeImage, int gridSize) {
        try {
            BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, nativeImage.getPixel(x, y));
            CapturedFrame captured = new CapturedFrame(image, gridSize);
            return new ResizeToGridProcessor().process(new CenterSquareCropProcessor().process(captured));
        } finally { nativeImage.close(); }
    }
}
