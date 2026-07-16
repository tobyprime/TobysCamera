package dev.tobyscamera.fabric.viewfinder;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.NativePixelFormat;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
        int size = Math.min(width - 40, height - 80);
        int left = (width - size) / 2;
        int top = (height - size - 30) / 2;
        graphics.blit(textureId, left, top, size, size, 0.0f, 1.0f, 0.0f, 1.0f);
        graphics.drawCenteredString(font, "%d x %d maps".formatted(frame.gridSize(), frame.gridSize()), width / 2, top + size + 8, 0xFFFFFFFF);
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
}
