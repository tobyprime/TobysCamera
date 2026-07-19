package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.NativeImageConverter;
import dev.tobyscamera.fabric.camera.PrintLayout;
import dev.tobyscamera.fabric.camera.PhotoPreviewProcessor;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PreviewScreen extends Screen {
    private static final ExecutorService PREVIEW_PROCESSOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera photo preview processor"); thread.setDaemon(true); return thread;
    });
    private final CapturedFrame frame;
    private final Consumer<PhotoPreviewProcessor.Result> usePhoto;
    private final Runnable retake;
    private final PhotoPreviewProcessor previewProcessor = new PhotoPreviewProcessor();
    private Identifier textureId;
    private boolean released;

    private int printSize;
    private MapTileEncoder.DitheringMode ditheringMode = MapTileEncoder.DEFAULT_DITHERING;
    private final PreviewResultGate<PhotoPreviewProcessor.Result> previewResult = new PreviewResultGate<>();
    private int previewImageWidth;
    private int previewImageHeight;
    private Future<?> previewTask;
    private Button usePhotoButton;

    public PreviewScreen(CapturedFrame frame, Consumer<PhotoPreviewProcessor.Result> usePhoto, Runnable retake) {
        super(Component.translatable("tobyscamera.preview.title"));
        this.frame = frame;
        this.usePhoto = usePhoto;
        this.retake = retake;
        this.printSize = frame.gridSize();
    }

    @Override
    protected void init() {
        releaseTexture();
        textureId = Identifier.fromNamespaceAndPath("tobyscamera", "preview/" + UUID.randomUUID());
        released = false;
        requestPreviewRefresh();
        int buttonY = height - 32;
        List<Integer> sizes = java.util.stream.IntStream.rangeClosed(1, frame.gridSize()).boxed().toList();
        addRenderableWidget(CycleButton.builder(this::ditheringValueLabel, ditheringMode)
                .withValues(List.of(MapTileEncoder.DitheringMode.OFF, MapTileEncoder.DitheringMode.FLOYD_STEINBERG))
                .create(width / 2 - 100, buttonY - 48, 200, 20, Component.translatable("tobyscamera.preview.dithering_label"), (button, value) -> { ditheringMode = value; requestPreviewRefresh(); }));
        addRenderableWidget(CycleButton.builder(PreviewScreen::resolutionValueLabel, printSize)
                .withValues(sizes)
                .displayOnlyValue()
                .create(width / 2 - 75, buttonY - 24, 150, 20, Component.translatable("tobyscamera.preview.print_label"), (button, value) -> { printSize = value; requestPreviewRefresh(); }));
        addRenderableWidget(Button.builder(Component.translatable("tobyscamera.preview.retake"), button -> closeForRetake()).bounds(width / 2 - 155, buttonY, 150, 20).build());
        usePhotoButton = addRenderableWidget(Button.builder(Component.translatable("tobyscamera.preview.use_photo"), button -> closeForUse()).bounds(width / 2 + 5, buttonY, 150, 20).build());
        usePhotoButton.active = previewResult.ready();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        if (!previewResult.ready()) {
            graphics.centeredText(font, Component.translatable("tobyscamera.preview.processing"), width / 2, height / 2, 0xFFFFFFFF);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        TextureBlit blit = textureBlit(20, 20, previewImageWidth, previewImageHeight, width - 40, height - 104);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            blit.left(),
            blit.top(),
            0.0f,
            0.0f,
            blit.width(),
            blit.height(),
            blit.sourceWidth(),
            blit.sourceHeight(),
            blit.textureWidth(),
            blit.textureHeight()
        );
        graphics.centeredText(font, localizedPrintLabel(printSize), width / 2, blit.top() + blit.height() + 8, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { closeForRetake(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() { cancelPreviewTask(); releaseTexture(); }

    private void closeForUse() {
        if (!previewResult.ready()) return;
        PhotoPreviewProcessor.Result photo = previewResult.result();
        if (photo == null) return;
        releaseTexture();
        usePhoto.accept(photo);
    }
    private void closeForRetake() { releaseTexture(); retake.run(); }
    private void releaseTexture() { if (!released && textureId != null) { minecraft.getTextureManager().release(textureId); released = true; } }

    private void requestPreviewRefresh() {
        cancelPreviewTask();
        int revision = previewResult.request();
        int requestedPrintSize = printSize;
        MapTileEncoder.DitheringMode requestedDithering = ditheringMode;
        if (usePhotoButton != null) usePhotoButton.active = false;
        previewTask = PREVIEW_PROCESSOR.submit(() -> {
            try {
            PhotoPreviewProcessor.Result result = previewProcessor.process(frame, requestedPrintSize, requestedDithering);
            NativeImage image = NativeImageConverter.fromBufferedImage(result.image());
            minecraft.execute(() -> publishPreview(revision, result, image));
            } catch (CancellationException ignored) { }
        });
    }
    private void cancelPreviewTask() { if (previewTask != null) { previewTask.cancel(true); previewTask = null; } }

    private void publishPreview(int revision, PhotoPreviewProcessor.Result photo, NativeImage image) {
        if (released || minecraft.screen != this || !previewResult.publish(revision, photo)) { image.close(); return; }
        previewImageWidth = image.getWidth();
        previewImageHeight = image.getHeight();
        minecraft.getTextureManager().register(textureId, new DynamicTexture(() -> "tobyscamera-preview", image));
        if (usePhotoButton != null) usePhotoButton.active = true;
    }

    static PrintLayout printLayout(CapturedFrame frame, int printSize) {
        return PrintLayout.forMaximumSide(printSize, frame.composition().aspectRatio());
    }

    private Component localizedPrintLabel(int size) {
        PrintLayout layout = printLayout(frame, size);
        return Component.translatable("tobyscamera.preview.print", size, layout.gridWidth(), layout.gridHeight());
    }

    private Component ditheringValueLabel(MapTileEncoder.DitheringMode mode) { return Component.translatable(mode == MapTileEncoder.DitheringMode.FLOYD_STEINBERG ? "tobyscamera.preview.dithering.floyd_steinberg" : "tobyscamera.preview.dithering.off"); }
    static Component resolutionValueLabel(int size) { return Component.translatable("tobyscamera.preview.resolution", size); }

    static TextureBlit textureBlit(int availableLeft, int availableTop, int textureWidth, int textureHeight, int availableWidth, int availableHeight) {
        if (textureWidth < 1 || textureHeight < 1 || availableWidth < 1 || availableHeight < 1) throw new IllegalArgumentException("dimensions must be positive");
        double scale = Math.min((double) availableWidth / textureWidth, (double) availableHeight / textureHeight);
        int width = (int) Math.round(textureWidth * scale);
        int height = (int) Math.round(textureHeight * scale);
        int left = availableLeft + (availableWidth - width) / 2;
        int top = availableTop + (availableHeight - height) / 2;
        return new TextureBlit(left, top, width, height, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    record TextureBlit(int left, int top, int width, int height, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) { }
}
