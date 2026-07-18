package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.UploadProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.Locale;

public final class ViewfinderOverlay {
    private static final int MASK_COLOR = 0xB0000000;
    private static final int LENS_SHADOW = 0x8C000000;
    private static final int LENS_DEEP_SHADOW = 0xC8000000;
    private static final int HUD_PANEL = 0x8C000000;
    private static final int HUD_TEXT = 0xE8F5FFF4;
    private static final int HUD_MUTED_TEXT = 0xB8D8E4D6;
    private static final int HUD_ACCENT = 0xE0D6FF74;
    private static final int HUD_RECORDING = 0xE0FF2F2F;
    private static final int GRID_COLOR = 0x68FFFFFF;
    private final ViewfinderSession session;
    private final KeyMapping zoomIn;
    private final KeyMapping zoomOut;
    private final KeyMapping gridKey;
    private final KeyMapping compositionKey;
    private final KeyMapping shutterKey;
    private final KeyMapping modeKey;
    private final KeyMapping fpsKey;
    private final IntSupplier remainingFilm;
    private final Supplier<UploadProgress> uploadProgress;
    private int shutterTicks;

    public ViewfinderOverlay(ViewfinderSession session, KeyMapping zoomIn, KeyMapping zoomOut, KeyMapping gridKey,
            KeyMapping compositionKey, KeyMapping shutterKey, KeyMapping modeKey, KeyMapping fpsKey, IntSupplier remainingFilm,
            Supplier<UploadProgress> uploadProgress) {
        this.session = session;
        this.zoomIn = zoomIn;
        this.zoomOut = zoomOut;
        this.gridKey = gridKey;
        this.compositionKey = compositionKey;
        this.shutterKey = shutterKey;
        this.modeKey = modeKey;
        this.fpsKey = fpsKey;
        this.remainingFilm = remainingFilm;
        this.uploadProgress = uploadProgress;
    }

    public void flashShutter() { shutterTicks = 3; }
    public void tick() { if (shutterTicks > 0) shutterTicks--; }

    public void extractRenderState(GuiGraphicsExtractor graphics) {
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
        drawLensBorder(graphics, left, top, frameWidth, frameHeight);
        drawGrid(graphics, left, top, frameWidth, frameHeight);
        drawCameraHud(graphics, minecraft, left, top, frameWidth, frameHeight);
        if (session.state() == ViewfinderState.UPLOADING) drawUploadProgress(graphics, minecraft, left, top, frameWidth, frameHeight);
        else drawHint(graphics, minecraft, left, top, hintComponent(keyName(zoomIn), keyName(zoomOut), keyName(gridKey),
                keyName(compositionKey), keyName(shutterKey)), frameWidth, frameHeight);
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
        return "[%s/%s] zoom  [%s] grid  [%s] composition  [%s] shutter  [Right Mouse] close"
                .formatted(zoomIn, zoomOut, grid, composition, shutter);
    }
    static Component hintComponent(String zoomIn, String zoomOut, String grid, String composition, String shutter) {
        return Component.translatable("tobyscamera.viewfinder.hint", zoomIn, zoomOut, grid, composition, shutter);
    }

    static String filmLabel(int remainingFilm) { return "FILM %02d".formatted(Math.max(0, remainingFilm)); }
    static String modeLabel(CaptureMode mode, int fps, String modeKey, String fpsKey) {
        return mode == CaptureMode.VIDEO ? "VIDEO %dFPS  [%s] mode  %s fps".formatted(fps, modeKey, fpsKey) : "PHOTO  [%s] mode".formatted(modeKey);
    }
    static boolean showsFilm(int remainingFilm) { return remainingFilm >= 0; }
    static String statusLabel(ViewfinderState state, CaptureMode mode) {
        if (state == ViewfinderState.UPLOADING) return "UPL";
        if (state == ViewfinderState.CAPTURING) return mode == CaptureMode.VIDEO ? "REC" : "CAP";
        if (state == ViewfinderState.AWAITING_GRANT) return "WAIT";
        return mode == CaptureMode.VIDEO ? "VIDEO" : "PHOTO";
    }
    static String zoomLabel(float zoom) { return String.format(Locale.ROOT, "x%.2f", zoom); }
    static String aspectLabel(String aspectRatio) { return "AR " + aspectRatio; }

    private void drawUploadProgress(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int top, int width, int height) {
        UploadProgress progress = uploadProgress.get();
        int barWidth = Math.min(240, width - 24), barLeft = left + (width - barWidth) / 2, barTop = top + height / 2;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 8, 0xB0000000);
        graphics.fill(barLeft, barTop, barLeft + (int) Math.round(barWidth * progress.fraction()), barTop + 8, 0xE0FFFFFF);
        graphics.centeredText(minecraft.font, Component.translatable("tobyscamera.viewfinder.uploading", progress.percentage()), left + width / 2, barTop - 12, HUD_TEXT);
    }

    private void drawCameraHud(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int top, int width, int height) {
        HudLayout layout = hudLayout(left, top, width, height, 0);
        String status = statusLabel(session.state(), session.mode());
        int statusColor = status.equals("REC") ? HUD_RECORDING : HUD_ACCENT;
        drawReadout(graphics, minecraft, Component.literal(status), layout.statusLeft(), layout.statusTop(), statusColor);
        if (status.equals("REC")) graphics.fill(layout.statusLeft() + minecraft.font.width(status) + 14, layout.statusTop() + 5,
                layout.statusLeft() + minecraft.font.width(status) + 20, layout.statusTop() + 11, HUD_RECORDING);
        Component mode = Component.literal(modeLabel(session.mode(), session.videoFps(), keyName(modeKey), keyName(fpsKey)));
        graphics.text(minecraft.font, mode, layout.statusLeft(), layout.statusTop() + 18, HUD_MUTED_TEXT, true);
        int film = remainingFilm.getAsInt();
        if (showsFilm(film)) drawReadoutRight(graphics, minecraft, Component.literal(filmLabel(film)), layout.safeRight(), layout.statusTop(), HUD_TEXT);
        String bottomLeft = zoomLabel(session.targetZoom()) + "  " + aspectLabel(session.composition().aspectRatio().toString());
        drawReadout(graphics, minecraft, Component.literal(bottomLeft), layout.safeLeft(), layout.exposureTop(), HUD_TEXT);
        graphics.text(minecraft.font, Component.literal(gridLabel(session.grid())), layout.safeLeft(), layout.gridTop(), HUD_MUTED_TEXT, true);
    }

    private static String gridLabel(CompositionGrid grid) {
        return switch (grid) {
            case NONE -> "GRID OFF";
            case THIRDS -> "GRID 3x3";
            case CROSSHAIR -> "GRID +";
        };
    }

    private static void drawHint(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int top, Component hint, int frameWidth, int frameHeight) {
        HudLayout layout = hudLayout(left, top, frameWidth, frameHeight, minecraft.font.width(hint) + 10);
        graphics.fill(layout.hintLeft(), layout.hintTop(), layout.hintLeft() + layout.hintWidth(), layout.hintTop() + 14, HUD_PANEL);
        graphics.text(minecraft.font, hint, layout.hintLeft() + 5, layout.hintTop() + 3, HUD_MUTED_TEXT, true);
    }

    private static String keyName(KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }

    static HudLayout hudLayout(int left, int top, int width, int height, int preferredHintWidth) {
        int inset = 10;
        int safeLeft = left + inset;
        int safeTop = top + inset;
        int safeRight = left + width - inset;
        int safeBottom = top + height - inset;
        int hintWidth = Math.min(Math.max(0, preferredHintWidth), Math.max(0, safeRight - safeLeft));
        int hintLeft = safeLeft + Math.max(0, (safeRight - safeLeft - hintWidth) / 2);
        int hintTop = safeBottom - 16;
        int exposureTop = hintTop - 30;
        return new HudLayout(safeLeft, safeTop, safeRight, safeBottom, safeLeft, safeTop,
                hintLeft, hintTop, hintWidth, exposureTop, exposureTop - 14);
    }

    private static void drawReadout(GuiGraphicsExtractor graphics, Minecraft minecraft, Component text, int left, int top, int color) {
        int width = minecraft.font.width(text) + 10;
        graphics.fill(left, top, left + width, top + 15, HUD_PANEL);
        graphics.text(minecraft.font, text, left + 5, top + 4, color, true);
    }

    private static void drawReadoutRight(GuiGraphicsExtractor graphics, Minecraft minecraft, Component text, int right, int top, int color) {
        int width = minecraft.font.width(text) + 10;
        drawReadout(graphics, minecraft, text, right - width, top, color);
    }

    private static void drawLensBorder(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        LensBorderLayout layout = lensBorderLayout(left, top, width, height);
        int rim = layout.rim();
        int inner = Math.max(3, rim / 3);
        int right = left + width;
        int bottom = top + height;
        graphics.fill(layout.outerLeft(), layout.outerTop(), layout.outerRight(), top, LENS_DEEP_SHADOW);
        graphics.fill(layout.outerLeft(), bottom, layout.outerRight(), layout.outerBottom(), LENS_DEEP_SHADOW);
        graphics.fill(layout.outerLeft(), top, left, bottom, LENS_DEEP_SHADOW);
        graphics.fill(right, top, layout.outerRight(), bottom, LENS_DEEP_SHADOW);
        graphics.fill(left, top - inner, right, top, LENS_SHADOW);
        graphics.fill(left, bottom, right, bottom + inner, LENS_SHADOW);
        graphics.fill(left - inner, top, left, bottom, LENS_SHADOW);
        graphics.fill(right, top, right + inner, bottom, LENS_SHADOW);
        drawCornerBrackets(graphics, layout.bracketLeft(), layout.bracketTop(), layout.bracketWidth(), layout.bracketHeight());
    }

    private static int lensRim(int width, int height) { return Math.max(8, Math.min(width, height) / 28); }

    static LensBorderLayout lensBorderLayout(int left, int top, int width, int height) {
        int rim = lensRim(width, height);
        return new LensBorderLayout(rim, left - rim, top - rim, left + width + rim, top + height + rim,
                left, top, width, height);
    }

    private static void drawCornerBrackets(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        int length = Math.max(18, Math.min(52, Math.min(width, height) / 8));
        int stroke = 2;
        int color = 0xD8FFFFFF;
        graphics.fill(left, top, left + length, top + stroke, color);
        graphics.fill(left, top, left + stroke, top + length, color);
        graphics.fill(left + width - length, top, left + width, top + stroke, color);
        graphics.fill(left + width - stroke, top, left + width, top + length, color);
        graphics.fill(left, top + height - stroke, left + length, top + height, color);
        graphics.fill(left, top + height - length, left + stroke, top + height, color);
        graphics.fill(left + width - length, top + height - stroke, left + width, top + height, color);
        graphics.fill(left + width - stroke, top + height - length, left + width, top + height, color);
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
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
    record HudLayout(int safeLeft, int safeTop, int safeRight, int safeBottom, int statusLeft, int statusTop,
            int hintLeft, int hintTop, int hintWidth, int exposureTop, int gridTop) { }
    record LensBorderLayout(int rim, int outerLeft, int outerTop, int outerRight, int outerBottom,
            int bracketLeft, int bracketTop, int bracketWidth, int bracketHeight) { }
}
