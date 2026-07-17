package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.NativeImageConverter;
import dev.tobyscamera.fabric.camera.PrintLayout;
import dev.tobyscamera.fabric.video.TemporaryVideoRecording;
import dev.tobyscamera.fabric.video.VideoEncoder;
import dev.tobyscamera.fabric.video.VideoFrameRange;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Video confirmation using the exact encoded map palette for the selected retained frame. */
public final class VideoPreviewScreen extends Screen {
    private static final ExecutorService PREVIEW_PROCESSOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera video preview processor"); thread.setDaemon(true); return thread;
    });
    private final TemporaryVideoRecording recording;
    private final int fps;
    private final int maximumPrintSize;
    private final AspectRatio aspectRatio;
    private final Consumer<VideoEncoder> useVideo;
    private final Runnable cancel;
    private int startFrame, endFrame, displayedFrame, printSize;
    private MapTileEncoder.DitheringMode dithering = MapTileEncoder.DEFAULT_DITHERING;
    private Identifier textureId;
    private int imageWidth, imageHeight;
    private int previewRevision;
    private boolean previewReady;
    private Future<?> previewTask;

    public VideoPreviewScreen(TemporaryVideoRecording recording, int fps, int maximumPrintSize, AspectRatio aspectRatio,
            Consumer<VideoEncoder> useVideo, Runnable cancel) {
        super(Component.translatable("tobyscamera.video_preview.title"));
        this.recording = recording; this.fps = fps; this.maximumPrintSize = maximumPrintSize; this.aspectRatio = aspectRatio;
        this.useVideo = useVideo; this.cancel = cancel; this.endFrame = Math.max(0, recording.frameCount() - 1); this.printSize = maximumPrintSize;
    }

    @Override protected void init() {
        textureId = Identifier.fromNamespaceAndPath("tobyscamera", "video-preview/" + UUID.randomUUID());
        int bottom = height - 32, controlsLeft = width / 2 - 175; List<Integer> frames = java.util.stream.IntStream.range(0, recording.frameCount()).boxed().toList();
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.video_preview.start", value), startFrame).withValues(frames)
                .create(controlsLeft, bottom - 72, 110, 20, Component.empty(), (button, value) -> { startFrame = Math.min(value, endFrame); refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.video_preview.end", value), endFrame).withValues(frames)
                .create(controlsLeft + 120, bottom - 72, 110, 20, Component.empty(), (button, value) -> { endFrame = Math.max(value, startFrame); refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.video_preview.frame", value), displayedFrame).withValues(frames)
                .create(controlsLeft + 240, bottom - 72, 110, 20, Component.empty(), (button, value) -> { displayedFrame = value; refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.video_preview.print_size", value), printSize)
                .withValues(java.util.stream.IntStream.rangeClosed(1, maximumPrintSize).boxed().toList())
                .create(controlsLeft + 55, bottom - 48, 110, 20, Component.empty(), (button, value) -> { printSize = value; refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.video_preview.dithering", Component.translatable(value == MapTileEncoder.DitheringMode.FLOYD_STEINBERG ? "tobyscamera.preview.dithering.floyd_steinberg" : "tobyscamera.preview.dithering.off")), dithering)
                .withValues(List.of(MapTileEncoder.DitheringMode.OFF, MapTileEncoder.DitheringMode.FLOYD_STEINBERG))
                .create(controlsLeft + 175, bottom - 48, 150, 20, Component.empty(), (button, value) -> { dithering = value; refresh(); }));
        addRenderableWidget(Button.builder(Component.translatable("tobyscamera.video_preview.cancel"), button -> cancel()).bounds(width / 2 - 155, bottom, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tobyscamera.video_preview.print"), button -> use()).bounds(width / 2 + 5, bottom, 150, 20).build());
        refresh();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        if (!previewReady) {
            graphics.drawCenteredString(font, Component.translatable("tobyscamera.video_preview.processing"), width / 2, height / 2, 0xFFFFFFFF);
        } else if (textureId != null) {
            double scale = Math.min((double) (width - 40) / imageWidth, (double) (height - 130) / imageHeight);
            int drawWidth = (int) (imageWidth * scale), drawHeight = (int) (imageHeight * scale), left = (width - drawWidth) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, left, 20, 0, 0, drawWidth, drawHeight, imageWidth, imageHeight, imageWidth, imageHeight);
            graphics.drawCenteredString(font, "Frames %d-%d · %d FPS · %dx%d maps".formatted(startFrame, endFrame, fps, layout().gridWidth(), layout().gridHeight()), width / 2, 20 + drawHeight + 6, 0xFFFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { cancel(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { cancelPreviewTask(); if (textureId != null) minecraft.getTextureManager().release(textureId); }

    private void use() { useVideo.accept(encoder()); }
    private void cancel() { cancel.run(); }
    private PrintLayout layout() { return PrintLayout.forMaximumSide(printSize, aspectRatio); }
    private VideoEncoder encoder() { return new VideoEncoder(recording, new VideoFrameRange(startFrame, endFrame, recording.frameCount()), layout(), dithering); }
    private void refresh() {
        cancelPreviewTask();
        int revision = ++previewRevision;
        int frame = Math.clamp(displayedFrame, startFrame, endFrame);
        int encoderFrame = frame - startFrame;
        VideoEncoder requestedEncoder = encoder();
        previewReady = false;
        previewTask = PREVIEW_PROCESSOR.submit(() -> {
            try {
                NativeImage image = NativeImageConverter.fromBufferedImage(new MapTileEncoder().palettePreview(requestedEncoder.frame(encoderFrame)));
                minecraft.execute(() -> publishPreview(revision, image));
            } catch (CancellationException ignored) { } catch (IOException exception) { minecraft.execute(this::cancel); }
        });
    }
    private void cancelPreviewTask() { if (previewTask != null) { previewTask.cancel(true); previewTask = null; } }
    private void publishPreview(int revision, NativeImage image) {
        if (minecraft.screen != this || revision != previewRevision) { image.close(); return; }
        imageWidth = image.getWidth(); imageHeight = image.getHeight();
        minecraft.getTextureManager().register(textureId, new DynamicTexture(() -> "tobyscamera-video-preview", image));
        previewReady = true;
    }
}
