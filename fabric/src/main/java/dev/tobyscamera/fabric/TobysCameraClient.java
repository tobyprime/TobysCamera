package dev.tobyscamera.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.CenterSquareCropProcessor;
import dev.tobyscamera.fabric.camera.HeldCameraChecker;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import dev.tobyscamera.fabric.camera.PhotoUploadController;
import dev.tobyscamera.fabric.camera.ResizeToGridProcessor;
import dev.tobyscamera.fabric.input.CameraKeyCategory;
import dev.tobyscamera.fabric.input.CameraKeyBindings;
import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.CaptureService;
import dev.tobyscamera.fabric.viewfinder.PreviewScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import dev.tobyscamera.fabric.viewfinder.ViewfinderInputController;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSession;
import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import java.awt.image.BufferedImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public final class TobysCameraClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PhotoUploadController UPLOADS = new PhotoUploadController();
    private static final ViewfinderSession VIEWFINDER = new ViewfinderSession();
    private static final ViewfinderOverlay OVERLAY = new ViewfinderOverlay(VIEWFINDER);
    private static final CaptureService CAPTURE = new CaptureService();
    private static final ViewfinderInputController INPUTS = new ViewfinderInputController(VIEWFINDER, UPLOADS::requestCapture);
    private static final KeyMapping VIEWFINDER_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.viewfinder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            CameraKeyCategory.value()));
    private static final KeyMapping GRID_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_IN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.zoom_in", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET,
            CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_OUT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.zoom_out", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET,
            CameraKeyCategory.value()));
    private static final KeyMapping SHUTTER_KEY = KeyBindingHelper.registerKeyBinding(CameraKeyBindings.shutter());

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPayload.TYPE, CameraPayload.CODEC);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("tobyscamera", "viewfinder"), (graphics, delta) -> OVERLAY.render(graphics));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !HeldCameraChecker.isCamera(player.getItemInHand(hand))) return InteractionResult.PASS;
            if (VIEWFINDER.state() == ViewfinderState.CLOSED) VIEWFINDER.open();
            return InteractionResult.FAIL;
        });
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE, (payload, context) -> handleServerPacket(context.client(), PacketCodec.decode(payload.data())));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LOGGER.info("Registered camera shutter binding with default key Enter; configure it in Controls > TobysCamera.");
    }

    private void handleServerPacket(net.minecraft.client.Minecraft client, dev.tobyscamera.common.protocol.CameraPacket packet) {
        LOGGER.info("Received camera server packet {} while viewfinder is {}.", packet.getClass().getSimpleName(), VIEWFINDER.state());
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
        while (ZOOM_IN_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER) VIEWFINDER.adjustZoom(1.0);
        while (ZOOM_OUT_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER) VIEWFINDER.adjustZoom(-1.0);
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING) CAPTURE.tick();
    }

    public static void captureWorldBeforeHand(net.minecraft.client.Minecraft client) {
        if (VIEWFINDER.state() != ViewfinderState.CAPTURING || !CAPTURE.captureReady()) return;
        int gridSize = CAPTURE.takeGridSize();
        Screenshot.takeScreenshot(client.getMainRenderTarget(), nativeImage -> openPreview(client, toFrame(nativeImage, gridSize)));
    }

    public static boolean closeViewfinder() {
        return INPUTS.close();
    }

    public static boolean handleShutterKey(KeyEvent event) {
        if (!SHUTTER_KEY.matches(event)) return false;
        ViewfinderState before = VIEWFINDER.state();
        boolean accepted = INPUTS.pressShutter();
        LOGGER.info("Camera shutter key event matched while viewfinder is {}; capture request accepted={}.", before, accepted);
        return accepted;
    }

    public static void logViewfinderKeyEvent(int action, KeyEvent event) {
        if (action != GLFW.GLFW_PRESS || VIEWFINDER.state() == ViewfinderState.CLOSED) return;
        LOGGER.info("Viewfinder keyboard event key={} scancode={} shutterBinding={}; matches={}",
                event.key(), event.scancode(), SHUTTER_KEY.saveString(), SHUTTER_KEY.matches(event));
    }

    public static float viewfinderZoom() {
        return VIEWFINDER.zoomActive() ? VIEWFINDER.targetZoom() : 1.0f;
    }

    private static void openPreview(net.minecraft.client.Minecraft client, CapturedFrame frame) {
        if (!VIEWFINDER.captureComplete()) return;
        client.setScreen(new PreviewScreen(frame,
                () -> { if (VIEWFINDER.beginUpload()) { if (UPLOADS.confirm(frame)) VIEWFINDER.finishUpload(); else VIEWFINDER.retake(); } client.setScreen(null); },
                () -> { VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private static CapturedFrame toFrame(com.mojang.blaze3d.platform.NativeImage nativeImage, int gridSize) {
        try {
            BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, NativePixelFormat.toArgb(nativeImage.getPixel(x, y)));
            CapturedFrame captured = new CapturedFrame(image, gridSize);
            return new ResizeToGridProcessor().process(new CenterSquareCropProcessor().process(captured));
        } finally { nativeImage.close(); }
    }
}
