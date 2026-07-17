package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.UploadProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.Locale;

public final class ViewfinderOverlay {
    private static final int MASK_COLOR = 0xB0000000;
    private static final int BORDER_COLOR = 0xE0FFFFFF;
    private static final int GRID_COLOR = 0x80FFFFFF;
    private final ViewfinderSession session;
    private final KeyMapping zoomIn;
    private final KeyMapping zoomOut;
    private final KeyMapping gridKey;
    private final KeyMapping compositionKey;
    private final KeyMapping shutterKey;
    private final KeyMapping modeKey;
    private final KeyMapping fpsUpKey;
    private final KeyMapping fpsDownKey;
    private final IntSupplier remainingFilm;
    private final Supplier<UploadProgress> uploadProgress;
    private int shutterTicks;

    public ViewfinderOverlay(ViewfinderSession session, KeyMapping zoomIn, KeyMapping zoomOut, KeyMapping gridKey,
            KeyMapping compositionKey, KeyMapping shutterKey, KeyMapping modeKey, KeyMapping fpsUpKey, KeyMapping fpsDownKey, IntSupplier remainingFilm,
            Supplier<UploadProgress> uploadProgress) {
        this.session = session;
        this.zoomIn = zoomIn;
        this.zoomOut = zoomOut;
        this.gridKey = gridKey;
        this.compositionKey = compositionKey;
        this.shutterKey = shutterKey;
        this.modeKey = modeKey;
        this.fpsUpKey = fpsUpKey;
        this.fpsDownKey = fpsDownKey;
        this.remainingFilm = remainingFilm;
        this.uploadProgress = uploadProgress;
    }

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
        int film = remainingFilm.getAsInt();
        if (showsFilm(film)) graphics.drawString(minecraft.font, Component.translatable("tobyscamera.viewfinder.film", Math.max(0, film)), left + 6, top + 6, BORDER_COLOR, true);
        Component mode = session.mode() == CaptureMode.VIDEO
                ? Component.translatable("tobyscamera.viewfinder.video_mode", session.videoFps(), keyName(modeKey), keyName(fpsDownKey), keyName(fpsUpKey))
                : Component.translatable("tobyscamera.viewfinder.photo_mode", keyName(modeKey));
        graphics.drawString(minecraft.font, mode, left + 6, top + 18, BORDER_COLOR, true);
        if (session.state() == ViewfinderState.UPLOADING) drawUploadProgress(graphics, minecraft, left, top, frameWidth, frameHeight);
        else drawHint(graphics, minecraft, left, top, Component.translatable("tobyscamera.viewfinder.hint", String.format(Locale.ROOT, "%.2f", session.targetZoom()), session.composition().aspectRatio().toString(),
                keyName(zoomIn), keyName(zoomOut), keyName(gridKey), keyName(compositionKey), keyName(shutterKey)),
                frameWidth);
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

    static String hintText(float zoom, String aspectRatio, String zoomIn, String zoomOut, String grid,
            String composition, String shutter) {
        return "x%.2f  %s  [%s/%s] zoom  [%s] grid  [%s] composition  [%s] shutter  [Esc] close"
                .formatted(zoom, aspectRatio, zoomIn, zoomOut, grid, composition, shutter);
    }

    static String filmLabel(int remainingFilm) { return "Film: " + Math.max(0, remainingFilm); }
    static String modeLabel(CaptureMode mode, int fps, String modeKey, String fpsDown, String fpsUp) {
        return mode == CaptureMode.VIDEO ? "VIDEO %d FPS  [%s] mode  [%s/%s] fps".formatted(fps, modeKey, fpsDown, fpsUp) : "PHOTO  [%s] mode".formatted(modeKey);
    }
    static boolean showsFilm(int remainingFilm) { return remainingFilm >= 0; }

    private void drawUploadProgress(GuiGraphics graphics, Minecraft minecraft, int left, int top, int width, int height) {
        UploadProgress progress = uploadProgress.get();
        int barWidth = Math.min(240, width - 24), barLeft = left + (width - barWidth) / 2, barTop = top + height / 2;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 8, 0xB0000000);
        graphics.fill(barLeft, barTop, barLeft + (int) Math.round(barWidth * progress.fraction()), barTop + 8, 0xE0FFFFFF);
        graphics.drawCenteredString(minecraft.font, Component.translatable("tobyscamera.viewfinder.uploading", progress.percentage()), left + width / 2, barTop - 12, BORDER_COLOR);
    }
    private static void drawHint(GuiGraphics graphics, Minecraft minecraft, int left, int top, Component hint, int frameWidth) {
        int hintWidth = Math.min(frameWidth - 12, minecraft.font.width(hint) + 8);
        graphics.fill(left + 2, top + 30, left + 2 + hintWidth, top + 44, 0x90000000);
        graphics.drawString(minecraft.font, hint, left + 6, top + 32, BORDER_COLOR, true);
    }

    private static String keyName(KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }

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
