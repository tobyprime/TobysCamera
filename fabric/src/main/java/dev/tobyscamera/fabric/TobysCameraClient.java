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
import dev.tobyscamera.fabric.camera.UploadProgress;
import dev.tobyscamera.fabric.input.CameraKeyBindings;
import dev.tobyscamera.fabric.input.CameraKeyCategory;
import dev.tobyscamera.fabric.input.ShutterMouseInput;
import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.CaptureService;
import dev.tobyscamera.fabric.viewfinder.PreviewScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderControlsScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderInputController;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSession;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSettings;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSettingsStore;
import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Client camera flow for still photographs. */
public final class TobysCameraClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PhotoUploadController UPLOADS = new PhotoUploadController();
    private static final ViewfinderSession VIEWFINDER = new ViewfinderSession();
    private static final CaptureService CAPTURE = new CaptureService();
    private static final ExecutorService PHOTO_PROCESSOR_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera photo processor");
        thread.setDaemon(true);
        return thread;
    });
    private static ViewfinderSettingsStore settingsStore;
    private static ViewfinderSettings pendingSettings;
    private static long settingsSaveAfterMillis;
    private static final ViewfinderInputController INPUTS = new ViewfinderInputController(
            VIEWFINDER, TobysCameraClient::heldCameraGridSize, TobysCameraClient::startLocalCapture);
    private static final KeyMapping VIEWFINDER_KEY = ClientPlatform.registerKeyMapping(new KeyMapping(
            "key.tobyscamera.viewfinder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CameraKeyCategory.value()));
    private static final KeyMapping GRID_KEY = ClientPlatform.registerKeyMapping(new KeyMapping(
            "key.tobyscamera.grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_IN_KEY = ClientPlatform.registerKeyMapping(new KeyMapping(
            "key.tobyscamera.zoom_in", InputConstants.Type.KEYSYM, CameraKeyBindings.defaultZoomInKey(), CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_OUT_KEY = ClientPlatform.registerKeyMapping(new KeyMapping(
            "key.tobyscamera.zoom_out", InputConstants.Type.KEYSYM, CameraKeyBindings.defaultZoomOutKey(), CameraKeyCategory.value()));
    private static final KeyMapping SHUTTER_KEY = ClientPlatform.registerKeyMapping(CameraKeyBindings.shutter());
    private static final KeyMapping COMPOSITION_KEY = ClientPlatform.registerKeyMapping(new KeyMapping(
            "key.tobyscamera.composition", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CameraKeyCategory.value()));
    private static final ViewfinderOverlay OVERLAY = new ViewfinderOverlay(VIEWFINDER, ZOOM_IN_KEY, ZOOM_OUT_KEY,
            GRID_KEY, COMPOSITION_KEY, SHUTTER_KEY, TobysCameraClient::heldCameraFilm, TobysCameraClient::uploadProgress);

    @Override
    public void onInitializeClient() {
        settingsStore = new ViewfinderSettingsStore(FabricLoader.getInstance().getConfigDir().resolve("tobyscamera-client.properties"));
        VIEWFINDER.applySettings(settingsStore.load());
        VIEWFINDER.setSettingsListener(settings -> { pendingSettings = settings; settingsSaveAfterMillis = System.currentTimeMillis() + 300L; });
        ClientPlatform.registerPayloads();
        ClientPlatform.registerViewfinderHud(OVERLAY);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !HeldCameraChecker.isCamera(player.getItemInHand(hand))) return InteractionResult.PASS;
            if (VIEWFINDER.state() == ViewfinderState.CLOSED) VIEWFINDER.open();
            return InteractionResult.FAIL;
        });
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE,
                (payload, context) -> handleServerPacket(PacketCodec.decode(payload.data())));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static void handleServerPacket(dev.tobyscamera.common.protocol.CameraPacket packet) {
        UPLOADS.handleServerPacket(packet);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) {
            VIEWFINDER.finishUpload();
        }
    }

    private void tick(net.minecraft.client.Minecraft client) {
        savePendingSettings();
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
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING) CAPTURE.tick();
        UPLOADS.tick();
    }

    private static void savePendingSettings() {
        if (pendingSettings == null || System.currentTimeMillis() < settingsSaveAfterMillis) return;
        try {
            settingsStore.save(pendingSettings);
            pendingSettings = null;
        } catch (IOException exception) {
            settingsSaveAfterMillis = System.currentTimeMillis() + 5_000L;
            LOGGER.warn("Could not save TobysCamera client settings", exception);
        }
    }

    public static void captureWorldBeforeHand(net.minecraft.client.Minecraft client) {
        if (VIEWFINDER.state() != ViewfinderState.CAPTURING || !CAPTURE.captureReady()) return;
        int gridSize = CAPTURE.takeGridSize();
        Screenshot.takeScreenshot(client.getMainRenderTarget(), nativeImage -> PHOTO_PROCESSOR_EXECUTOR.execute(() -> {
            CapturedFrame frame = toFrame(nativeImage, gridSize);
            client.execute(() -> openPreview(client, frame));
        }));
    }

    public static boolean closeViewfinder() {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof ViewfinderControlsScreen) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
            return true;
        }
        return INPUTS.close();
    }

    private static void toggleCompositionEditor(net.minecraft.client.Minecraft client) {
        if (VIEWFINDER.state() != ViewfinderState.VIEWFINDER) return;
        if (client.screen instanceof ViewfinderControlsScreen) client.setScreen(null);
        else client.setScreen(new ViewfinderControlsScreen(VIEWFINDER, COMPOSITION_KEY));
    }

    private static int heldCameraGridSize() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumGridSize(player.getMainHandItem()), HeldCameraChecker.maximumGridSize(player.getOffhandItem()));
    }

    private static int heldCameraFilm() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return HeldCameraChecker.isCamera(player.getMainHandItem())
                ? HeldCameraChecker.remainingFilm(player.getMainHandItem()) : HeldCameraChecker.remainingFilm(player.getOffhandItem());
    }

    private static void startLocalCapture(int gridSize) {
        UPLOADS.requestCapture();
        OVERLAY.flashShutter();
        CAPTURE.requestAfterNextFrame(gridSize);
    }

    public static boolean handleShutterKey(KeyEvent event) {
        return shutterInputAllowed() && SHUTTER_KEY.matches(event) && INPUTS.pressShutter();
    }

    public static boolean handleShutterMouse(net.minecraft.client.input.MouseButtonEvent event) {
        return ShutterMouseInput.consumeRightClickClose(event, TobysCameraClient::shutterInputAllowed, TobysCameraClient::closeViewfinder)
                || ShutterMouseInput.consume(SHUTTER_KEY, event, TobysCameraClient::shutterInputAllowed, INPUTS::pressShutter);
    }

    private static boolean shutterInputAllowed() {
        return !(net.minecraft.client.Minecraft.getInstance().screen instanceof ViewfinderControlsScreen);
    }

    private static UploadProgress uploadProgress() { return UPLOADS.progress(); }
    public static void logViewfinderKeyEvent(int action, KeyEvent event) { }
    public static float viewfinderZoom() { return VIEWFINDER.zoomActive() ? VIEWFINDER.targetZoom() : 1.0f; }
    public static float viewfinderRollRadians() { return VIEWFINDER.zoomActive() ? (float) Math.toRadians(VIEWFINDER.composition().rollDegrees()) : 0.0f; }
    public static boolean hideNameTagsForPhoto() { return PhotoRenderPolicy.hideNameTags(VIEWFINDER.state(), CAPTURE.captureReady()); }

    private static void openPreview(net.minecraft.client.Minecraft client, CapturedFrame frame) {
        if (!VIEWFINDER.captureComplete()) return;
        client.setScreen(new PreviewScreen(frame,
                photo -> { if (VIEWFINDER.beginUpload() && !UPLOADS.confirm(photo.photo(), photo.bagPreview())) VIEWFINDER.retake(); client.setScreen(null); },
                () -> { VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private static CapturedFrame toFrame(com.mojang.blaze3d.platform.NativeImage nativeImage, int gridSize) {
        try {
            return new ResizeToGridProcessor().process(new CompositionCropProcessor().process(
                    new CapturedFrame(toImage(nativeImage), gridSize, VIEWFINDER.composition())));
        } finally { nativeImage.close(); }
    }

    private static BufferedImage toImage(com.mojang.blaze3d.platform.NativeImage nativeImage) {
        BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            image.setRGB(x, y, NativePixelFormat.toArgb(nativeImage.getPixel(x, y)));
        }
        return image;
    }
}
