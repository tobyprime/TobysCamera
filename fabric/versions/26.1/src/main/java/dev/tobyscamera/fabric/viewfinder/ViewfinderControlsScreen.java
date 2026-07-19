package dev.tobyscamera.fabric.viewfinder;

import java.util.Locale;
import java.util.function.IntSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A transparent screen which keeps the live viewfinder visible behind a compact bottom control strip. */
public final class ViewfinderControlsScreen extends Screen {
    private static final int PANEL_WIDTH = 720;
    private static final int PANEL_HEIGHT = 72;
    private final ViewfinderSession session;
    private final ViewfinderControlModel controls;
    private final KeyMapping toggleKey;
    private final IntSupplier maximumGridSize;

    public ViewfinderControlsScreen(ViewfinderSession session, KeyMapping toggleKey, IntSupplier maximumGridSize) {
        super(Component.translatable("tobyscamera.viewfinder.controls.title"));
        this.session = session;
        this.controls = new ViewfinderControlModel(session);
        this.toggleKey = toggleKey;
        this.maximumGridSize = maximumGridSize;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 16);
        int left = (width - panelWidth) / 2;
        int top = height - PANEL_HEIGHT - 10;
        int rowOne = top + 7;
        int rowTwo = top + 34;
        int rollWidth = Math.min(190, Math.max(100, panelWidth / 4));
        addRenderableWidget(new RollSlider(left + 6, rowOne, rollWidth));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.viewfinder.controls.ratio", value), session.composition().aspectRatio())
                .withValues(ViewfinderControlModel.RATIOS)
                .create(left + rollWidth + 12, rowOne, 118, 20, Component.empty(), (button, value) -> session.setAspectRatio(value)));
        EditBox customRatio = new EditBox(font, left + rollWidth + 136, rowOne, 78, 20, Component.translatable("tobyscamera.composition.custom_ratio"));
        customRatio.setValue(session.composition().aspectRatio().toString());
        addRenderableWidget(customRatio);
        addRenderableWidget(Button.builder(Component.translatable("tobyscamera.composition.set"), button -> {
            if (controls.setCustomRatio(customRatio.getValue())) customRatio.setValue(session.composition().aspectRatio().toString());
        }).bounds(left + rollWidth + 218, rowOne, 45, 20).build());
        addRenderableWidget(new ZoomSlider(left + Math.min(panelWidth - 196, rollWidth + 270), rowOne, Math.min(190, panelWidth - rollWidth - 280)));
        addRenderableWidget(CycleButton.builder(value -> Component.translatable("tobyscamera.viewfinder.controls.grid", gridName(value)), session.grid())
                .withValues(CompositionGrid.values())
                .create(left + 6, rowTwo, 135, 20, Component.empty(), (button, value) -> setGrid(value)));
        int maximum = Math.max(1, maximumGridSize.getAsInt());
        int selected = Math.clamp(session.printSize(), 1, maximum);
        addRenderableWidget(CycleButton.builder(PreviewScreen::resolutionValueLabel, selected)
                .withValues(java.util.stream.IntStream.rangeClosed(1, maximum).boxed().toList()).displayOnlyValue()
                .create(left + 146, rowTwo, 125, 20, Component.translatable("tobyscamera.preview.print_label"),
                        (button, value) -> session.setPrintSize(value, maximumGridSize.getAsInt())));
        addRenderableWidget(Button.builder(Component.translatable("tobyscamera.viewfinder.controls.done"), button -> onClose())
                .bounds(left + panelWidth - 70, rowTwo, 64, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(PANEL_WIDTH, width - 16);
        int left = (width - panelWidth) / 2;
        int top = height - PANEL_HEIGHT - 10;
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xD0101010);
        graphics.outline(left, top, panelWidth, PANEL_HEIGHT, 0xE0FFFFFF);
        graphics.text(font, title, left + 6, top - 10, 0xFFFFFFFF, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (toggleKey.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }

    private void setGrid(CompositionGrid value) {
        while (session.grid() != value) session.cycleGrid();
    }

    private static Component gridName(CompositionGrid grid) {
        return Component.translatable("tobyscamera.viewfinder.controls.grid." + grid.name().toLowerCase(java.util.Locale.ROOT));
    }

    private final class RollSlider extends AbstractSliderButton {
        RollSlider(int x, int y, int width) { super(x, y, width, 20, Component.empty(), session.composition().rollDegrees() / 360.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("tobyscamera.viewfinder.controls.roll", String.format(Locale.ROOT, "%.1f", value * 360.0))); }
        @Override protected void applyValue() { controls.setRollDegrees((float) (value * 360.0)); }
    }

    private final class ZoomSlider extends AbstractSliderButton {
        ZoomSlider(int x, int y, int width) { super(x, y, width, 20, Component.empty(), (session.targetZoom() - 1.0) / 3.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("tobyscamera.viewfinder.controls.zoom", String.format(Locale.ROOT, "%.2f", 1.0 + value * 3.0))); }
        @Override protected void applyValue() { controls.setZoom((float) (1.0 + value * 3.0)); }
    }
}
