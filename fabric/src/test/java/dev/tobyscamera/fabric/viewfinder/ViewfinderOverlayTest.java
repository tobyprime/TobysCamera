package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewfinderOverlayTest {
    @Test
    void buildsHintsFromTheCurrentlyBoundKeyNames() {
        assertEquals("x1.50  4:3  [Q/E] zoom  [H] grid  [R] composition  [F] shutter  [Esc] close",
                ViewfinderOverlay.hintText(1.5f, "4:3", "Q", "E", "H", "R", "F"));
    }

    @Test
    void rendersRemainingFilmAsANonNegativeLabel() {
        assertEquals("Film: 9", ViewfinderOverlay.filmLabel(9));
        assertEquals("Film: 0", ViewfinderOverlay.filmLabel(-1));
        assertEquals(false, ViewfinderOverlay.showsFilm(-1));
    }
}
