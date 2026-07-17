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
import dev.tobyscamera.fabric.input.CameraKeyCategory;
import dev.tobyscamera.fabric.input.CameraKeyBindings;
import dev.tobyscamera.fabric.input.ShutterMouseInput;
import dev.tobyscamera.fabric.net.CameraPayload;
import dev.tobyscamera.fabric.viewfinder.CaptureService;
import dev.tobyscamera.fabric.viewfinder.ViewfinderControlsScreen;
import dev.tobyscamera.fabric.viewfinder.PreviewScreen;
import dev.tobyscamera.fabric.viewfinder.ViewfinderOverlay;
import dev.tobyscamera.fabric.viewfinder.ViewfinderInputController;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSession;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSettings;
import dev.tobyscamera.fabric.viewfinder.ViewfinderSettingsStore;
import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import dev.tobyscamera.fabric.viewfinder.CaptureMode;
import dev.tobyscamera.fabric.viewfinder.VideoPreviewScreen;
import dev.tobyscamera.fabric.viewfinder.VideoShutterTransition;
import dev.tobyscamera.fabric.video.TemporaryVideoRecording;
import dev.tobyscamera.fabric.video.VideoCaptureService;
import dev.tobyscamera.fabric.video.VideoCaptureFormat;
import dev.tobyscamera.fabric.video.VideoUploadController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.image.BufferedImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
    private static final VideoUploadController VIDEO_UPLOADS = new VideoUploadController(TobysCameraClient::sendPacket, System::currentTimeMillis,
            reason -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                LOGGER.error(reason);
                VIEWFINDER.cancelUpload();
            }));
    private static final ExecutorService VIDEO_FRAME_WRITER_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera video writer"); thread.setDaemon(true); return thread;
    });
    private static final ExecutorService PHOTO_PROCESSOR_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera photo processor"); thread.setDaemon(true); return thread;
    });
    private static final dev.tobyscamera.fabric.video.VideoFrameWriteQueue VIDEO_FRAME_WRITES = new dev.tobyscamera.fabric.video.VideoFrameWriteQueue(VIDEO_FRAME_WRITER_EXECUTOR);
    private static TemporaryVideoRecording videoRecording;
    private static boolean videoReadbackPending;
    private static boolean videoPreviewRequested;
    private static boolean videoCaptureRequested;
    private static VideoCaptureFormat videoCaptureFormat;
    private static int videoFrameLimit;
    private static int videoCapturedFrames;
    private static int videoGridSize;
    private static long videoCaptureStartedAt;
    private static long videoCaptureFinishedAt;
    private static int recordedVideoFps;
    private static ViewfinderSettingsStore settingsStore;
    private static ViewfinderSettings pendingSettings;
    private static long settingsSaveAfterMillis;
    private static final ViewfinderInputController INPUTS = new ViewfinderInputController(
            VIEWFINDER, TobysCameraClient::heldCameraCaptureGridSize, TobysCameraClient::heldCameraSupportsVideo, TobysCameraClient::startLocalCapture);
    private static final KeyMapping VIEWFINDER_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.viewfinder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            CameraKeyCategory.value()));
    private static final KeyMapping GRID_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_IN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.zoom_in", InputConstants.Type.KEYSYM, CameraKeyBindings.defaultZoomInKey(),
            CameraKeyCategory.value()));
    private static final KeyMapping ZOOM_OUT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.zoom_out", InputConstants.Type.KEYSYM, CameraKeyBindings.defaultZoomOutKey(),
            CameraKeyCategory.value()));
    private static final KeyMapping SHUTTER_KEY = KeyBindingHelper.registerKeyBinding(CameraKeyBindings.shutter());
    private static final KeyMapping COMPOSITION_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.composition", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CameraKeyCategory.value()));
    private static final KeyMapping MODE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CameraKeyCategory.value()));
    private static final KeyMapping FPS_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.tobyscamera.fps", InputConstants.Type.KEYSYM, CameraKeyBindings.defaultVideoFpsKey(), CameraKeyCategory.value()));
    private static final ViewfinderOverlay OVERLAY = new ViewfinderOverlay(VIEWFINDER, ZOOM_IN_KEY, ZOOM_OUT_KEY,
            GRID_KEY, COMPOSITION_KEY, SHUTTER_KEY, MODE_KEY, FPS_KEY, TobysCameraClient::heldCameraFilm, TobysCameraClient::uploadProgress);

    @Override
    public void onInitializeClient() {
        settingsStore = new ViewfinderSettingsStore(FabricLoader.getInstance().getConfigDir().resolve("tobyscamera-client.properties"));
        VIEWFINDER.applySettings(settingsStore.load());
        VIEWFINDER.setSettingsListener(settings -> { pendingSettings = settings; settingsSaveAfterMillis = System.currentTimeMillis() + 300L; });
        try { TemporaryVideoRecording.cleanupAbandoned(videoDirectory()); }
        catch (IOException exception) { LOGGER.warn("Could not clear abandoned camera video recordings", exception); }
        PayloadTypeRegistry.playC2S().register(CameraPayload.TYPE, CameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraPayload.TYPE, CameraPayload.CODEC);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("tobyscamera", "viewfinder"), (graphics, delta) -> OVERLAY.render(graphics));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (!HeldCameraChecker.isCamera(player.getItemInHand(hand))) return InteractionResult.PASS;
            if (VIEWFINDER.state() == ViewfinderState.CLOSED) VIEWFINDER.open();
            return InteractionResult.FAIL;
        });
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE, (payload, context) -> handleServerPacket(context.client(), PacketCodec.decode(payload.data())));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LOGGER.info("Registered camera shutter binding with default button Left Mouse; configure it in Controls > TobysCamera.");
    }

    private void handleServerPacket(net.minecraft.client.Minecraft client, dev.tobyscamera.common.protocol.CameraPacket packet) {
        LOGGER.info("Received camera server packet {} while viewfinder is {}.", packet.getClass().getSimpleName(), VIEWFINDER.state());
        UPLOADS.handleServerPacket(packet);
        VIDEO_UPLOADS.handleServerPacket(packet);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated || packet instanceof Packets.VideoCreated)
            VIEWFINDER.finishUpload();
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
        while (MODE_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER && (VIEWFINDER.mode() == CaptureMode.VIDEO || heldCameraSupportsVideo())) { VIEWFINDER.toggleMode(); if (VIEWFINDER.mode() == CaptureMode.VIDEO) VIEWFINDER.capVideoFps(heldCameraVideoFps()); }
        while (FPS_KEY.consumeClick()) if (VIEWFINDER.state() == ViewfinderState.VIEWFINDER && VIEWFINDER.mode() == CaptureMode.VIDEO && heldCameraSupportsVideo()) VIEWFINDER.adjustVideoFps(1, heldCameraVideoFps());
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING) CAPTURE.tick();
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING && VIEWFINDER.mode() == CaptureMode.VIDEO
                && !videoReadbackPending && videoCapturedFrames < videoFrameLimit && VIDEO_CAPTURE.captureDue(System.currentTimeMillis())) videoCaptureRequested = true;
        if (VIEWFINDER.state() == ViewfinderState.CAPTURING && VIEWFINDER.mode() == CaptureMode.VIDEO
                && videoCapturedFrames >= videoFrameLimit && !videoReadbackPending) finishVideoCaptureAtLimit();
        if (videoPreviewRequested && !videoReadbackPending) {
            videoPreviewRequested = false;
            VIDEO_FRAME_WRITES.flush(() -> net.minecraft.client.Minecraft.getInstance().execute(() -> openVideoPreview(net.minecraft.client.Minecraft.getInstance())));
        }
        VIDEO_UPLOADS.tick();
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
        if (VIEWFINDER.state() != ViewfinderState.CAPTURING) return;
        if (VIEWFINDER.mode() == CaptureMode.VIDEO) {
            if (videoRecording == null || videoReadbackPending || !videoCaptureRequested) return;
            videoCaptureRequested = false;
            videoReadbackPending = true;
            VideoCaptureFormat format = videoCaptureFormat;
            if (format == null) { videoReadbackPending = false; return; }
            Screenshot.takeScreenshot(client.getMainRenderTarget(), format.screenshotDownscale(), nativeImage -> {
                com.mojang.blaze3d.platform.NativeImage copy = new com.mojang.blaze3d.platform.NativeImage(format.outputWidth(), format.outputHeight(), false);
                nativeImage.resizeSubRectTo(format.cropLeft(), format.cropTop(), format.cropWidth(), format.cropHeight(), copy);
                nativeImage.close();
                TemporaryVideoRecording recording = videoRecording;
                if (recording == null || !VIDEO_FRAME_WRITES.submit(() -> {
                    try { recording.appendNativeImage(copy); }
                    catch (IOException exception) { LOGGER.error("Could not store video frame", exception); }
                    finally { copy.close(); }
                })) copy.close(); else videoCapturedFrames++;
                videoReadbackPending = false;
            });
            return;
        }
        if (!CAPTURE.captureReady()) return;
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
        else client.setScreen(new ViewfinderControlsScreen(VIEWFINDER, TobysCameraClient::heldCameraVideoFps, COMPOSITION_KEY));
    }

    private static int heldCameraGridSize() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumGridSize(player.getMainHandItem()),
                HeldCameraChecker.maximumGridSize(player.getOffhandItem()));
    }

    private static int heldCameraCaptureGridSize() { return VIEWFINDER.mode() == CaptureMode.VIDEO ? heldCameraVideoGridSize() : heldCameraGridSize(); }

    private static int heldCameraVideoGridSize() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumVideoGridSize(player.getMainHandItem()), HeldCameraChecker.maximumVideoGridSize(player.getOffhandItem()));
    }

    private static int heldCameraFilm() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return HeldCameraChecker.isCamera(player.getMainHandItem())
                ? HeldCameraChecker.remainingFilm(player.getMainHandItem())
                : HeldCameraChecker.remainingFilm(player.getOffhandItem());
    }

    private static void startLocalCapture(int gridSize) {
        UPLOADS.requestCapture();
        OVERLAY.flashShutter();
        if (VIEWFINDER.mode() == CaptureMode.VIDEO) {
            try {
                videoRecording = TemporaryVideoRecording.create(videoDirectory());
                videoPreviewRequested = false;
                videoCaptureRequested = false;
                videoCapturedFrames = 0;
                videoCaptureStartedAt = System.currentTimeMillis();
                videoCaptureFinishedAt = 0L;
                recordedVideoFps = VIEWFINDER.videoFps();
                videoFrameLimit = heldCameraVideoFrameLimit();
                videoGridSize = gridSize;
                var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
                videoCaptureFormat = VideoCaptureFormat.forCamera(gridSize, VIEWFINDER.composition().aspectRatio(), target.width, target.height);
                VIEWFINDER.clampVideoFpsToMaximum(heldCameraVideoFps());
                VIDEO_CAPTURE.start(VIEWFINDER.videoFps(), System.currentTimeMillis());
            } catch (IOException exception) {
                LOGGER.error("Could not initialize temporary video recording", exception);
                VIEWFINDER.close();
            }
            return;
        }
        CAPTURE.requestAfterNextFrame(gridSize);
    }

    public static boolean handleShutterKey(KeyEvent event) {
        if (!shutterInputAllowed() || !SHUTTER_KEY.matches(event)) return false;
        ViewfinderState before = VIEWFINDER.state();
        boolean accepted = pressShutter();
        LOGGER.info("Camera shutter key event matched while viewfinder is {}; capture request accepted={}.", before, accepted);
        return accepted;
    }

    public static boolean handleShutterMouse(net.minecraft.client.input.MouseButtonEvent event) {
        if (ShutterMouseInput.consumeRightClickClose(event, TobysCameraClient::shutterInputAllowed, TobysCameraClient::closeViewfinder)) {
            LOGGER.info("Closed camera viewfinder from right mouse button.");
            return true;
        }
        ViewfinderState before = VIEWFINDER.state();
        boolean accepted = ShutterMouseInput.consume(SHUTTER_KEY, event, TobysCameraClient::shutterInputAllowed, TobysCameraClient::pressShutter);
        if (SHUTTER_KEY.matchesMouse(event)) {
            LOGGER.info("Camera shutter mouse event matched while viewfinder is {}; capture request accepted={}.", before, accepted);
        }
        return accepted;
    }

    private static boolean pressShutter() {
        ViewfinderState before = VIEWFINDER.state();
        CaptureMode mode = VIEWFINDER.mode();
        boolean accepted = INPUTS.pressShutter();
        if (accepted && VideoShutterTransition.stopsRecording(before, mode, VIEWFINDER.state())) {
            VIDEO_CAPTURE.stop();
            videoCaptureFinishedAt = System.currentTimeMillis();
            videoCaptureRequested = false;
            videoPreviewRequested = true;
        }
        return accepted;
    }

    private static boolean shutterInputAllowed() {
        return !(net.minecraft.client.Minecraft.getInstance().screen instanceof ViewfinderControlsScreen);
    }

    private static UploadProgress uploadProgress() {
        return VIEWFINDER.mode() == CaptureMode.VIDEO ? VIDEO_UPLOADS.progress() : UPLOADS.progress();
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
        return VIEWFINDER.mode() == CaptureMode.VIDEO ? videoCaptureRequested : PhotoRenderPolicy.hideNameTags(VIEWFINDER.state(), CAPTURE.captureReady());
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

    private static boolean heldCameraSupportsVideo() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && (HeldCameraChecker.supportsVideo(player.getMainHandItem()) || HeldCameraChecker.supportsVideo(player.getOffhandItem()));
    }

    private static int heldCameraVideoFrameLimit() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return 0;
        return Math.max(HeldCameraChecker.maximumVideoFrames(player.getMainHandItem()), HeldCameraChecker.maximumVideoFrames(player.getOffhandItem()));
    }

    private static void openVideoPreview(net.minecraft.client.Minecraft client) {
        TemporaryVideoRecording recording = videoRecording;
        if (recording == null || recording.frameCount() < 1) { discardVideoRecording(); VIEWFINDER.retake(); return; }
        int maximum = videoGridSize;
        if (maximum < 1) { discardVideoRecording(); VIEWFINDER.retake(); return; }
        var aspectRatio = videoCaptureFormat == null ? VIEWFINDER.composition().aspectRatio() : videoCaptureFormat.aspectRatio();
        long duration = Math.max(1L, (videoCaptureFinishedAt == 0L ? System.currentTimeMillis() : videoCaptureFinishedAt) - videoCaptureStartedAt);
        recordedVideoFps = dev.tobyscamera.common.video.VideoFrameRate.measured(recording.frameCount(), duration, heldCameraVideoFps());
        client.setScreen(new VideoPreviewScreen(recording, recordedVideoFps, maximum, aspectRatio,
                encoder -> submitVideo(client, encoder, recording),
                () -> { discardVideoRecording(); VIEWFINDER.retake(); client.setScreen(null); }));
    }

    private static void submitVideo(net.minecraft.client.Minecraft client, dev.tobyscamera.fabric.video.VideoEncoder encoder, TemporaryVideoRecording recording) {
        LOGGER.info("Video print confirmed: state={}, frames={}, maps={}x{}, fps={}",
                VIEWFINDER.state(), encoder.frameCount(), encoder.gridWidth(), encoder.gridHeight(), recordedVideoFps);
        if (!VIEWFINDER.beginUpload()) {
            LOGGER.warn("Video print was not submitted because the viewfinder is {} instead of PREVIEW.", VIEWFINDER.state());
            return;
        }
        if (!VIDEO_UPLOADS.begin(encoder, recording, recordedVideoFps)) {
            LOGGER.warn("Video print was not submitted because the upload controller rejected the local recording.");
            VIEWFINDER.cancelUpload();
            return;
        }
        videoRecording = null;
        client.setScreen(null);
    }

    private static void discardVideoRecording() { if (videoRecording != null) try { videoRecording.close(); } catch (IOException ignored) { } finally { videoRecording = null; videoCaptureFormat = null; } }
    private static void finishVideoCaptureAtLimit() { VIDEO_CAPTURE.stop(); videoCaptureFinishedAt = System.currentTimeMillis(); videoCaptureRequested = false; videoPreviewRequested = VIEWFINDER.pressShutter(videoGridSize); }
    private static Path videoDirectory() { return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("tobyscamera").resolve("videos"); }
    private static BufferedImage toImage(com.mojang.blaze3d.platform.NativeImage nativeImage) {
        BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, NativePixelFormat.toArgb(nativeImage.getPixel(x, y)));
        return image;
    }
    private static void sendPacket(dev.tobyscamera.common.protocol.CameraPacket packet) { ClientPlayNetworking.send(new CameraPayload(PacketCodec.encode(packet))); }
}
