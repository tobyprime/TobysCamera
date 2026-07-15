package dev.tobyscamera.fabric.viewfinder;

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
        int size = Math.min(width, height);
        int left = (width - size) / 2;
        int top = (height - size) / 2;
        graphics.fill(0, 0, width, top, MASK_COLOR);
        graphics.fill(0, top + size, width, height, MASK_COLOR);
        graphics.fill(0, top, left, top + size, MASK_COLOR);
        graphics.fill(left + size, top, width, top + size, MASK_COLOR);
        drawBorder(graphics, left, top, size);
        drawGrid(graphics, left, top, size);
        graphics.drawString(minecraft.font, "x%.2f  [/] zoom  G: %s  LMB: shutter  Esc: close".formatted(session.targetZoom(), session.grid().name().toLowerCase()), left + 6, top + size - 14, BORDER_COLOR, true);
        if (shutterTicks > 0) graphics.fill(left, top, left + size, top + size, 0xDD000000);
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int size) {
        int stroke = 2;
        graphics.fill(left, top, left + size, top + stroke, BORDER_COLOR);
        graphics.fill(left, top + size - stroke, left + size, top + size, BORDER_COLOR);
        graphics.fill(left, top, left + stroke, top + size, BORDER_COLOR);
        graphics.fill(left + size - stroke, top, left + size, top + size, BORDER_COLOR);
    }

    private void drawGrid(GuiGraphics graphics, int left, int top, int size) {
        if (session.grid() == CompositionGrid.THIRDS) {
            int third = size / 3;
            graphics.fill(left + third, top, left + third + 1, top + size, GRID_COLOR);
            graphics.fill(left + third * 2, top, left + third * 2 + 1, top + size, GRID_COLOR);
            graphics.fill(left, top + third, left + size, top + third + 1, GRID_COLOR);
            graphics.fill(left, top + third * 2, left + size, top + third * 2 + 1, GRID_COLOR);
        } else if (session.grid() == CompositionGrid.CROSSHAIR) {
            int middleX = left + size / 2, middleY = top + size / 2;
            graphics.fill(middleX, top, middleX + 1, top + size, GRID_COLOR);
            graphics.fill(left, middleY, left + size, middleY + 1, GRID_COLOR);
        }
    }
}
