package dev.tobyscamera.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.PacketCodec;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.CompositionCropProcessor;
import dev.tobyscamera.fabric.camera.HeldCameraChecker;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import dev.tobyscamera.fabric.camera.PhotoRenderPolicy;
import dev.tobyscamera.fabric.camera.PhotoUploadController;
import dev.tobyscamera.fabric.camera.ResizeToGridProcessor;
import dev.tobyscamera.fabric.input.CameraKeyCategory;
import dev.tobyscamera.fabric.input.CameraKeyBindings;
import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.CaptureService;
import dev.tobyscamera.fabric.viewfinder.CompositionScreen;
import dev.tobyscamera.fabric.viewfinder.PreviewScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import dev.tobyscamera.fabric.viewfinder.ViewfinderInputController;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSession;
import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import dev.tobyscamera.fabric.viewfinder.CaptureMode;
import dev.tobyscamera.fabric.viewfinder.VideoPreviewScreen;
import dev.tobyscamera.fabric.video.TemporaryVideoRecording;
import dev.tobyscamera.fabric.video.VideoCaptureService;
import dev.tobyscamera.fabric.video.VideoUploadController;
import java.io.IOException;
import java.nio.file.Path;
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
    private static final CaptureService CAPTURE = new CaptureService();
    private static final VideoCaptureService VIDEO_CAPTURE = new VideoCaptureService();
    private static final VideoUploadController VIDEO_UPLOADS = new VideoUploadController(TobysCameraClient::sendPacket, System::currentTimeMillis);
    private static TemporaryVideoRecording videoRecording;
    private static boolean videoFramePending;
    private static final ViewfinderInputController INPUTS = new ViewfinderInputController(
            VIEWFINDER, TobysCameraClient::heldCameraGridSize, TobysCameraClient::startLocalCapture);
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
    private static final KeyMapping COMPOSITION_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.composition", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CameraKeyCategory.value()));
    private static final KeyMapping MODE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CameraKeyCategory.value()));
    private static final KeyMapping FPS_UP_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.fps_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CameraKeyCategory.value()));
    private static final KeyMapping FPS_DOWN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.fps_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, CameraKeyCategory.value()));
    private static final ViewfinderOverlay OVERLAY = new ViewfinderOverlay(VIEWFINDER, ZOOM_IN_KEY, ZOOM_OUT_KEY,
            GRID_KEY, COMPOSITION_KEY, SHUTTER_KEY, MODE_KEY, FPS_UP_KEY, FPS_DOWN_KEY, TobysCameraClient::heldCameraFilm);

    @Override
    public void onInitializeClient() {
        try { TemporaryVideoRecording.cleanupAbandoned(videoDirectory()); }
        catch (IOException exception) { LOGGER.warn("Could not clear abandoned camera video recordings", exception); }
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
        VIDEO_UPLOADS.handleServerPacket(packet);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated || packet instanceof Packets.VideoCreated)
            VIEWFINDER.finishUpload();
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
        while (COMPOSITION_KEY.consumeClick()) toggleCompositionEditor(client);
        while (MODE_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER) VIEWFINDER.toggleMode();
        while (FPS_UP_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER && VIEWFINDER.mode() == CaptureMode.VIDEO) VIEWFINDER.adjustVideoFps(1, heldCameraVideoFps());
        while (FPS_DOWN_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER && VIEWFINDER.mode() == CaptureMode.VIDEO) VIEWFINDER.adjustVideoFps(-1, heldCameraVideoFps());
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING) CAPTURE.tick();
        VIDEO_UPLOADS.tick();
    }

    public static void captureWorldBeforeHand(net.minecraft.client.Minecraft client) {
        if (VIEWFINDER.state() != ViewfinderState.CAPTURING) return;
        if (VIEWFINDER.mode() == CaptureMode.VIDEO) {
            if (videoRecording == null || videoFramePending || !VIDEO_CAPTURE.captureDue(System.currentTimeMillis())) return;
            videoFramePending = true;
            Screenshot.takeScreenshot(client.getMainRenderTarget(), nativeImage -> {
                try { videoRecording.append(toImage(nativeImage)); }
                catch (IOException exception) { LOGGER.error("Could not store video frame", exception); }
                finally { nativeImage.close(); videoFramePending = false; }
            });
            return;
        }
        if (!CAPTURE.captureReady()) return;
        int gridSize = CAPTURE.takeGridSize();
        Screenshot.takeScreenshot(client.getMainRenderTarget(), nativeImage -> openPreview(client, toFrame(nativeImage, gridSize)));
    }

    public static boolean closeViewfinder() {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof CompositionScreen) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
            return true;
        }
        return INPUTS.close();
    }

    private static void toggleCompositionEditor(net.minecraft.client.Minecraft client) {
        if (VIEWFINDER.state() != ViewfinderState.VIEWFINDER) return;
        if (client.screen instanceof CompositionScreen) client.setScreen(null); else client.setScreen(new CompositionScreen(VIEWFINDER));
    }

    private static int heldCameraGridSize() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumGridSize(player.getMainHandItem()),
                HeldCameraChecker.maximumGridSize(player.getOffhandItem()));
    }

    private static int heldCameraFilm() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return HeldCameraChecker.isCamera(player.getMainHandItem())
                ? HeldCameraChecker.remainingFilm(player.getMainHandItem())
                : HeldCameraChecker.remainingFilm(player.getOffhandItem());
    }

    private static void startLocalCapture(int gridSize) {
        OVERLAY.flashShutter();
        if (VIEWFINDER.mode() == CaptureMode.VIDEO) {
            try {
                videoRecording = TemporaryVideoRecording.create(videoDirectory());
                VIDEO_CAPTURE.start(VIEWFINDER.videoFps(), System.currentTimeMillis());
            } catch (IOException exception) {
                LOGGER.error("Could not initialize temporary video recording", exception);
                VIEWFINDER.close();
            }
            return;
        }
        UPLOADS.requestCapture();
        CAPTURE.requestAfterNextFrame(gridSize);
    }

    public static boolean handleShutterKey(KeyEvent event) {
        if (!SHUTTER_KEY.matches(event)) return false;
        ViewfinderState before = VIEWFINDER.state();
        boolean accepted = INPUTS.pressShutter();
        if (accepted && before == ViewfinderState.CAPTURING && VIEWFINDER.mode() == CaptureMode.VIDEO && VIEWFINDER.state() == ViewfinderState.PREVIEW) {
            VIDEO_CAPTURE.stop();
            openVideoPreview(net.minecraft.client.Minecraft.getInstance());
        }
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

    public static float viewfinderRollRadians() {
        return VIEWFINDER.zoomActive() ? (float) Math.toRadians(VIEWFINDER.composition().rollDegrees()) : 0.0f;
    }

    public static boolean hideNameTagsForPhoto() {
        return PhotoRenderPolicy.hideNameTags(VIEWFINDER.state(), CAPTURE.captureReady());
    }

    private static void openPreview(net.minecraft.client.Minecraft client, CapturedFrame frame) {
        if (!VIEWFINDER.captureComplete()) return;
        client.setScreen(new PreviewScreen(frame,
                photo -> { if (VIEWFINDER.beginUpload() && !UPLOADS.confirm(photo)) VIEWFINDER.retake(); client.setScreen(null); },
                () -> { VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private static CapturedFrame toFrame(com.mojang.blaze3d.platform.NativeImage nativeImage, int gridSize) {
        try {
            BufferedImage image = toImage(nativeImage);
            CapturedFrame captured = new CapturedFrame(image, gridSize, VIEWFINDER.composition());
            return new ResizeToGridProcessor().process(new CompositionCropProcessor().process(captured));
        } finally { nativeImage.close(); }
    }

    private static int heldCameraVideoFps() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumVideoFps(player.getMainHandItem()), HeldCameraChecker.maximumVideoFps(player.getOffhandItem()));
    }

    private static void openVideoPreview(net.minecraft.client.Minecraft client) {
        TemporaryVideoRecording recording = videoRecording;
        if (recording == null || recording.frameCount() < 1) { discardVideoRecording(); VIEWFINDER.retake(); return; }
        int maximum = heldCameraGridSize();
        if (maximum < 1) { discardVideoRecording(); VIEWFINDER.retake(); return; }
        client.setScreen(new VideoPreviewScreen(recording, VIEWFINDER.videoFps(), maximum, VIEWFINDER.composition().aspectRatio(),
                encoder -> { if (VIEWFINDER.beginUpload() && VIDEO_UPLOADS.begin(encoder, recording, VIEWFINDER.videoFps())) { videoRecording = null; } else VIEWFINDER.retake(); client.setScreen(null); },
                () -> { discardVideoRecording(); VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private static void discardVideoRecording() { if (videoRecording != null) try { videoRecording.close(); } catch (IOException ignored) { } finally { videoRecording = null; } }
    private static Path videoDirectory() { return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("tobyscamera").resolve("videos"); }
    private static BufferedImage toImage(com.mojang.blaze3d.platform.NativeImage nativeImage) {
        BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, NativePixelFormat.toArgb(nativeImage.getPixel(x, y)));
        return image;
    }
    private static void sendPacket(dev.tobyscamera.common.protocol.CameraPacket packet) { ClientPlayNetworking.send(new CameraPayload(PacketCodec.encode(packet))); }
}
