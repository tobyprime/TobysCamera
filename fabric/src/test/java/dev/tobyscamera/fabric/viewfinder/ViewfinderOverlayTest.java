package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ViewfinderOverlayTest {
    private static final int FRAME_LEFT = 256;
    private static final int FRAME_TOP = 32;
    private static final int FRAME_SIZE = 512;

    @Test
    void buildsHintsFromTheCurrentlyBoundKeyNames() {
        assertEquals("[Q/E] zoom  [H] grid  [R] composition  [F] shutter  [Right Mouse] close",
                ViewfinderOverlay.hintText(1.5f, "4:3", "Q", "E", "H", "R", "F"));
    }

    @Test
    void buildsHintAsATranslatableComponent() {
        Component hint = ViewfinderOverlay.hintComponent("Q", "E", "H", "R", "F");
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, hint.getContents());

        assertEquals("tobyscamera.viewfinder.hint", contents.getKey());
        assertEquals(5, contents.getArgs().length);
    }

    @Test
    void rendersRemainingFilmAsANonNegativeLabel() {
        assertEquals("FILM 09", ViewfinderOverlay.filmLabel(9));
        assertEquals("FILM 00", ViewfinderOverlay.filmLabel(-1));
        assertEquals(false, ViewfinderOverlay.showsFilm(-1));
    }

    @Test
    void formatsDigitalCameraReadouts() {
        assertEquals("PHOTO", ViewfinderOverlay.statusLabel(ViewfinderState.VIEWFINDER, CaptureMode.PHOTO));
        assertEquals("REC", ViewfinderOverlay.statusLabel(ViewfinderState.CAPTURING, CaptureMode.VIDEO));
        assertEquals("UPL", ViewfinderOverlay.statusLabel(ViewfinderState.UPLOADING, CaptureMode.VIDEO));
        assertEquals("x2.25", ViewfinderOverlay.zoomLabel(2.25f));
        assertEquals("AR 16:9", ViewfinderOverlay.aspectLabel("16:9"));
    }

    @Test
    void formatsVideoModeWithOneFpsCycleKey() {
        assertEquals("VIDEO 5FPS  [M] mode  ] fps", ViewfinderOverlay.modeLabel(CaptureMode.VIDEO, 5, "M", "]"));
    }

    @Test
    void keepsHudInsideTheLensSafeArea() {
        ViewfinderOverlay.HudLayout layout = ViewfinderOverlay.hudLayout(FRAME_LEFT, FRAME_TOP, FRAME_SIZE, FRAME_SIZE, 260);

        assertEquals(266, layout.safeLeft());
        assertEquals(42, layout.safeTop());
        assertEquals(758, layout.safeRight());
        assertEquals(534, layout.safeBottom());
        assertEquals(layout.safeTop(), layout.statusTop());
        assertEquals(layout.safeLeft(), layout.statusLeft());
        assertEquals(layout.safeBottom() - 16, layout.hintTop());
        assertEquals(layout.hintTop() - 30, layout.exposureTop());
        assertEquals(layout.exposureTop() - 14, layout.gridTop());
    }

    @Test
    void clampsHintPanelToTheLensSafeArea() {
        ViewfinderOverlay.HudLayout layout = ViewfinderOverlay.hudLayout(FRAME_LEFT, FRAME_TOP, FRAME_SIZE, FRAME_SIZE, 900);

        assertEquals(layout.safeLeft(), layout.hintLeft());
        assertEquals(layout.safeRight() - layout.safeLeft(), layout.hintWidth());
    }

    @Test
    void placesLensDecorationOutsideTheCaptureFrame() {
        ViewfinderOverlay.LensBorderLayout layout = ViewfinderOverlay.lensBorderLayout(FRAME_LEFT, FRAME_TOP, FRAME_SIZE, FRAME_SIZE);

        assertEquals(18, layout.rim());
        assertEquals(238, layout.outerLeft());
        assertEquals(14, layout.outerTop());
        assertEquals(786, layout.outerRight());
        assertEquals(562, layout.outerBottom());
        assertEquals(FRAME_LEFT, layout.bracketLeft());
        assertEquals(FRAME_TOP, layout.bracketTop());
        assertEquals(FRAME_SIZE, layout.bracketWidth());
        assertEquals(FRAME_SIZE, layout.bracketHeight());
    }
}
