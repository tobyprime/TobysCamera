package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import dev.tobyscamera.fabric.camera.PrintCanvasProcessor;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PreviewScreen extends Screen {
    private final CapturedFrame frame;
    private final Consumer<MapTileEncoder.EncodedPhoto> usePhoto;
    private final Runnable retake;
    private final MapTileEncoder encoder = new MapTileEncoder();
    private Identifier textureId;
    private boolean released;

    private int printSize;
    private MapTileEncoder.DitheringMode ditheringMode = MapTileEncoder.DEFAULT_DITHERING;
    private MapTileEncoder.EncodedPhoto printPhoto;
    private int previewImageWidth;
    private int previewImageHeight;

    public PreviewScreen(CapturedFrame frame, Consumer<MapTileEncoder.EncodedPhoto> usePhoto, Runnable retake) {
        super(Component.literal("Camera Preview"));
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
        refreshPreviewTexture();
        int buttonY = height - 32;
        List<Integer> sizes = java.util.stream.IntStream.rangeClosed(1, frame.gridSize()).boxed().toList();
        addRenderableWidget(CycleButton.builder(this::ditheringLabel, ditheringMode)
                .withValues(List.of(MapTileEncoder.DitheringMode.OFF, MapTileEncoder.DitheringMode.FLOYD_STEINBERG))
                .create(width / 2 - 100, buttonY - 48, 200, 20, Component.empty(), (button, value) -> { ditheringMode = value; refreshPreviewTexture(); }));
        addRenderableWidget(CycleButton.builder(value -> Component.literal(printLabel(value)), printSize)
                .withValues(sizes)
                .create(width / 2 - 75, buttonY - 24, 150, 20, Component.empty(), (button, value) -> { printSize = value; refreshPreviewTexture(); }));
        addRenderableWidget(Button.builder(Component.literal("Retake"), button -> closeForRetake()).bounds(width / 2 - 155, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Use photo"), button -> closeForUse()).bounds(width / 2 + 5, buttonY, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
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
        graphics.drawCenteredString(font, printLabel(printSize), width / 2, blit.top() + blit.height() + 8, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { closeForRetake(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() { releaseTexture(); }

    private void closeForUse() { releaseTexture(); if (printPhoto != null) usePhoto.accept(printPhoto); }
    private void closeForRetake() { releaseTexture(); retake.run(); }
    private void releaseTexture() { if (!released && textureId != null) { minecraft.getTextureManager().release(textureId); released = true; } }

    private void refreshPreviewTexture() {
        BufferedImage canvas = new PrintCanvasProcessor().process(frame.image(), printLayout(frame, printSize));
        printPhoto = encoder.encode(canvas, ditheringMode);
        BufferedImage image = encoder.palettePreview(printPhoto);
        previewImageWidth = image.getWidth();
        previewImageHeight = image.getHeight();
        minecraft.getTextureManager().register(textureId, new DynamicTexture(() -> "tobyscamera-preview", nativeImage(image)));
    }

    static PrintLayout printLayout(CapturedFrame frame, int printSize) {
        return PrintLayout.forMaximumSide(printSize, frame.composition().aspectRatio());
    }

    private String printLabel(int size) {
        PrintLayout layout = printLayout(frame, size);
        return "Print %dx (%d×%d maps)".formatted(size, layout.gridWidth(), layout.gridHeight());
    }

    private Component ditheringLabel(MapTileEncoder.DitheringMode mode) {
        return Component.literal("Color dithering: " + (mode == MapTileEncoder.DitheringMode.FLOYD_STEINBERG ? "Floyd-Steinberg" : "Off"));
    }

    private static NativeImage nativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, NativePixelFormat.toAbgr(source.getRGB(x, y)));
        return image;
    }

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
