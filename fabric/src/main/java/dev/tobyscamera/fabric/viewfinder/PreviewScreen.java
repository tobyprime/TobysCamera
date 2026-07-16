package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PreviewScreen extends Screen {
    private final CapturedFrame frame;
    private final Runnable usePhoto;
    private final Runnable retake;
    private Identifier textureId;
    private boolean released;

    public PreviewScreen(CapturedFrame frame, Runnable usePhoto, Runnable retake) {
        super(Component.literal("Camera Preview"));
        this.frame = frame;
        this.usePhoto = usePhoto;
        this.retake = retake;
    }

    @Override
    protected void init() {
        textureId = Identifier.fromNamespaceAndPath("tobyscamera", "preview/" + UUID.randomUUID());
        minecraft.getTextureManager().register(textureId, new DynamicTexture(() -> "tobyscamera-preview", nativeImage(frame)));
        int buttonY = height - 32;
        addRenderableWidget(Button.builder(Component.literal("Retake"), button -> closeForRetake()).bounds(width / 2 - 155, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Use photo"), button -> closeForUse()).bounds(width / 2 + 5, buttonY, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        TextureBlit blit = textureBlit(20, 20, frame.image().getWidth(), frame.image().getHeight(), width - 40, height - 80);
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
        graphics.drawCenteredString(font, "%d x %d maps".formatted(frame.gridSize(), frame.gridSize()), width / 2, blit.top() + blit.height() + 8, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { closeForRetake(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() { releaseTexture(); }

    private void closeForUse() { releaseTexture(); usePhoto.run(); }
    private void closeForRetake() { releaseTexture(); retake.run(); }
    private void releaseTexture() { if (!released && textureId != null) { minecraft.getTextureManager().release(textureId); released = true; } }

    private static NativeImage nativeImage(CapturedFrame frame) {
        NativeImage image = new NativeImage(frame.image().getWidth(), frame.image().getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, NativePixelFormat.toAbgr(frame.image().getRGB(x, y)));
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
