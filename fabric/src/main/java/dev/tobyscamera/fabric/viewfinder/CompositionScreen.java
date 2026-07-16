package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import java.util.List;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CompositionScreen extends Screen {
    private static final List<AspectRatio> RATIOS = List.of(AspectRatio.of(1, 1), AspectRatio.of(4, 3), AspectRatio.of(3, 4), AspectRatio.of(3, 2), AspectRatio.of(2, 3), AspectRatio.of(16, 9), AspectRatio.of(9, 16));
    private final ViewfinderSession session;

    public CompositionScreen(ViewfinderSession session) { super(Component.literal("Camera composition")); this.session = session; }

    @Override protected void init() {
        int x = width / 2 - 100;
        addRenderableWidget(new RollSlider(x, height / 2 - 30, 200));
        addRenderableWidget(CycleButton.builder(value -> Component.literal("Ratio: " + value), session.composition().aspectRatio())
                .withValues(RATIOS).create(x, height / 2 - 4, 200, 20, Component.empty(), (button, value) -> session.setAspectRatio(value)));
        EditBox custom = new EditBox(font, x, height / 2 + 22, 120, 20, Component.literal("Custom ratio"));
        custom.setValue(session.composition().aspectRatio().toString());
        addRenderableWidget(custom);
        addRenderableWidget(Button.builder(Component.literal("Set"), button -> {
            try { session.setAspectRatio(AspectRatio.parse(custom.getValue())); } catch (IllegalArgumentException ignored) { }
        }).bounds(x + 125, height / 2 + 22, 75, 20).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    private final class RollSlider extends AbstractSliderButton {
        RollSlider(int x, int y, int width) { super(x, y, width, 20, Component.empty(), session.composition().rollDegrees() / 360.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Roll: %.1f°".formatted(value * 360.0))); }
        @Override protected void applyValue() { session.setRollDegrees((float) (value * 360.0)); }
    }
}
