package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import dev.tobyscamera.fabric.camera.PrintLayout;
import dev.tobyscamera.fabric.video.TemporaryVideoRecording;
import dev.tobyscamera.fabric.video.VideoEncoder;
import dev.tobyscamera.fabric.video.VideoFrameRange;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
    private final TemporaryVideoRecording recording;
    private final int fps;
    private final int maximumPrintSize;
    private final AspectRatio aspectRatio;
    private final Consumer<VideoEncoder> useVideo;
    private final Runnable cancel;
    private int startFrame, endFrame, displayedFrame, printSize;
    private MapTileEncoder.DitheringMode dithering = MapTileEncoder.DitheringMode.OFF;
    private Identifier textureId;
    private int imageWidth, imageHeight;

    public VideoPreviewScreen(TemporaryVideoRecording recording, int fps, int maximumPrintSize, AspectRatio aspectRatio,
            Consumer<VideoEncoder> useVideo, Runnable cancel) {
        super(Component.literal("Video confirmation"));
        this.recording = recording; this.fps = fps; this.maximumPrintSize = maximumPrintSize; this.aspectRatio = aspectRatio;
        this.useVideo = useVideo; this.cancel = cancel; this.endFrame = Math.max(0, recording.frameCount() - 1); this.printSize = maximumPrintSize;
    }

    @Override protected void init() {
        textureId = Identifier.fromNamespaceAndPath("tobyscamera", "video-preview/" + UUID.randomUUID());
        int bottom = height - 32; List<Integer> frames = java.util.stream.IntStream.range(0, recording.frameCount()).boxed().toList();
        addRenderableWidget(CycleButton.builder(value -> Component.literal("Start: " + value), startFrame).withValues(frames)
                .create(20, bottom - 72, 110, 20, Component.empty(), (button, value) -> { startFrame = Math.min(value, endFrame); refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.literal("End: " + value), endFrame).withValues(frames)
                .create(140, bottom - 72, 110, 20, Component.empty(), (button, value) -> { endFrame = Math.max(value, startFrame); refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.literal("Preview: " + value), displayedFrame).withValues(frames)
                .create(260, bottom - 72, 110, 20, Component.empty(), (button, value) -> { displayedFrame = value; refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.literal("Print " + value + "x"), printSize)
                .withValues(java.util.stream.IntStream.rangeClosed(1, maximumPrintSize).boxed().toList())
                .create(20, bottom - 48, 110, 20, Component.empty(), (button, value) -> { printSize = value; refresh(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.literal("Dither: " + value), dithering)
                .withValues(List.of(MapTileEncoder.DitheringMode.OFF, MapTileEncoder.DitheringMode.FLOYD_STEINBERG))
                .create(140, bottom - 48, 150, 20, Component.empty(), (button, value) -> { dithering = value; refresh(); }));
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel()).bounds(width / 2 - 155, bottom, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Print video"), button -> use()).bounds(width / 2 + 5, bottom, 150, 20).build());
        refresh();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        if (textureId != null) {
            double scale = Math.min((double) (width - 40) / imageWidth, (double) (height - 130) / imageHeight);
            int drawWidth = (int) (imageWidth * scale), drawHeight = (int) (imageHeight * scale), left = (width - drawWidth) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, left, 20, 0, 0, drawWidth, drawHeight, imageWidth, imageHeight, imageWidth, imageHeight);
            graphics.drawCenteredString(font, "Frames %d-%d · %d FPS · %dx%d maps".formatted(startFrame, endFrame, fps, layout().gridWidth(), layout().gridHeight()), width / 2, 20 + drawHeight + 6, 0xFFFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { cancel(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if (textureId != null) minecraft.getTextureManager().release(textureId); }

    private void use() { useVideo.accept(encoder()); }
    private void cancel() { cancel.run(); }
    private PrintLayout layout() { return PrintLayout.forMaximumSide(printSize, aspectRatio); }
    private VideoEncoder encoder() { return new VideoEncoder(recording, new VideoFrameRange(startFrame, endFrame, recording.frameCount()), layout(), dithering); }
    private void refresh() {
        try {
            int frame = Math.clamp(displayedFrame, startFrame, endFrame);
            BufferedImage image = new MapTileEncoder().palettePreview(encoder().frame(frame - startFrame));
            imageWidth = image.getWidth(); imageHeight = image.getHeight();
            minecraft.getTextureManager().register(textureId, new DynamicTexture(() -> "tobyscamera-video-preview", nativeImage(image)));
        } catch (IOException exception) { cancel(); }
    }
    private static NativeImage nativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, NativePixelFormat.toAbgr(source.getRGB(x, y)));
        return image;
    }
}
