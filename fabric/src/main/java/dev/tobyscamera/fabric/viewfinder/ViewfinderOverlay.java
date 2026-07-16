package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ViewfinderOverlay {
    private static final int MASK_COLOR = 0xB0000000;
    private static final int BORDER_COLOR = 0xE0FFFFFF;
    private static final int GRID_COLOR = 0x80FFFFFF;
    private final ViewfinderSession session;
    private int shutterTicks;

    public ViewfinderOverlay(ViewfinderSession session) { this.session = session; }

    public void flashShutter() { shutterTicks = 3; }
    public void tick() { if (shutterTicks > 0) shutterTicks--; }

    public void render(GuiGraphics graphics) {
        if (session.state() == ViewfinderState.CLOSED || session.state() == ViewfinderState.PREVIEW || session.captureHidden()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        Frame frame = frame(width, height, session.composition().aspectRatio());
        int left = frame.left();
        int top = frame.top();
        int frameWidth = frame.width();
        int frameHeight = frame.height();
        graphics.fill(0, 0, width, top, MASK_COLOR);
        graphics.fill(0, top + frameHeight, width, height, MASK_COLOR);
        graphics.fill(0, top, left, top + frameHeight, MASK_COLOR);
        graphics.fill(left + frameWidth, top, width, top + frameHeight, MASK_COLOR);
        drawBorder(graphics, left, top, frameWidth, frameHeight);
        drawGrid(graphics, left, top, frameWidth, frameHeight);
        graphics.drawString(minecraft.font, "x%.2f  %s  [/] zoom  G: %s  Enter: shutter  Esc: close".formatted(session.targetZoom(), session.composition().aspectRatio(), session.grid().name().toLowerCase()), left + 6, top + frameHeight - 14, BORDER_COLOR, true);
        if (shutterTicks > 0) graphics.fill(left, top, left + frameWidth, top + frameHeight, 0xDD000000);
    }

    static Frame frame(int screenWidth, int screenHeight, AspectRatio aspectRatio) {
        int width = screenWidth;
        int height = (int) Math.round(width / aspectRatio.value());
        if (height > screenHeight) {
            height = screenHeight;
            width = (int) Math.round(height * aspectRatio.value());
        }
        return new Frame((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int width, int height) {
        int stroke = 2;
        graphics.fill(left, top, left + width, top + stroke, BORDER_COLOR);
        graphics.fill(left, top + height - stroke, left + width, top + height, BORDER_COLOR);
        graphics.fill(left, top, left + stroke, top + height, BORDER_COLOR);
        graphics.fill(left + width - stroke, top, left + width, top + height, BORDER_COLOR);
    }

    private void drawGrid(GuiGraphics graphics, int left, int top, int width, int height) {
        if (session.grid() == CompositionGrid.THIRDS) {
            int thirdWidth = width / 3, thirdHeight = height / 3;
            graphics.fill(left + thirdWidth, top, left + thirdWidth + 1, top + height, GRID_COLOR);
            graphics.fill(left + thirdWidth * 2, top, left + thirdWidth * 2 + 1, top + height, GRID_COLOR);
            graphics.fill(left, top + thirdHeight, left + width, top + thirdHeight + 1, GRID_COLOR);
            graphics.fill(left, top + thirdHeight * 2, left + width, top + thirdHeight * 2 + 1, GRID_COLOR);
        } else if (session.grid() == CompositionGrid.CROSSHAIR) {
            int middleX = left + width / 2, middleY = top + height / 2;
            graphics.fill(middleX, top, middleX + 1, top + height, GRID_COLOR);
            graphics.fill(left, middleY, left + width, middleY + 1, GRID_COLOR);
        }
    }

    record Frame(int left, int top, int width, int height) { }
}
