package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

class TileMapRendererTest {
    @Test
    void startsBlankAndDefensivelyCopiesNewPixels() {
        TileMapRenderer renderer = new TileMapRenderer();
        MapCanvas blankCanvas = mock(MapCanvas.class);
        renderer.render(null, blankCanvas, null);
        verify(blankCanvas).setPixel(1, 1, (byte) 0);

        byte[] pixels = new byte[16_384];
        pixels[129] = 42;

        renderer.setPixels(pixels);
        pixels[129] = 24;

        MapCanvas canvas = mock(MapCanvas.class);
        renderer.render(null, canvas, null);

        verify(canvas).setPixel(1, 1, (byte) 42);
    }

    @Test
    void clearPixelsReleasesPixelsAndRendersABlankTile() throws ReflectiveOperationException {
        byte[] pixels = new byte[16_384];
        pixels[129] = 42;
        TileMapRenderer renderer = new TileMapRenderer(pixels);

        renderer.clearPixels();

        Field pixelsField = TileMapRenderer.class.getDeclaredField("pixels");
        pixelsField.setAccessible(true);
        assertNull(pixelsField.get(renderer));
        MapCanvas canvas = mock(MapCanvas.class);
        renderer.render(null, canvas, null);

        verify(canvas).setPixel(1, 1, (byte) 0);
    }
}
