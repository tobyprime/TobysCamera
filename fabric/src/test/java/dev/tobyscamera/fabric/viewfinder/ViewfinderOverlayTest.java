package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.fabric.camera.AspectRatio;
import org.junit.jupiter.api.Test;

class ViewfinderOverlayTest {
    @Test
    void formatsLayoutCostAndFiniteFilmCapacityForTheSelectedPrint() {
        ViewfinderOverlay.PreflightReadout readout = ViewfinderOverlay.preflightReadout(4, 6, AspectRatio.of(3, 2), 64);

        assertEquals("4x3 maps", readout.layout());
        assertEquals("12 film", readout.cost());
        assertEquals("film 64 \u00b7 5 shots \u00b7 max 6x6", readout.capacity());
    }

    @Test
    void identifiesCamerasWithUnlimitedFilm() {
        ViewfinderOverlay.PreflightReadout readout = ViewfinderOverlay.preflightReadout(6, 6, AspectRatio.of(1, 1), -1);

        assertEquals("6x6 maps", readout.layout());
        assertEquals("no film required", readout.cost());
        assertEquals("max 6x6", readout.capacity());
    }

    @Test
    void clampsTheSelectedPrintSideToTheCameraMaximum() {
        ViewfinderOverlay.PreflightReadout readout = ViewfinderOverlay.preflightReadout(9, 6, AspectRatio.of(3, 2), 64);

        assertEquals("6x4 maps", readout.layout());
        assertEquals("24 film", readout.cost());
        assertEquals("film 64 \u00b7 2 shots \u00b7 max 6x6", readout.capacity());
    }
}
