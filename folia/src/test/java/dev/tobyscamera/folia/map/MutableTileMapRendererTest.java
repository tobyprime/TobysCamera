package dev.tobyscamera.folia.map;

import static org.mockito.Mockito.verify;

import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class MutableTileMapRendererTest {
    @Test
    void rendersTheMostRecentlySuppliedTile() {
        MutableTileMapRenderer renderer = new MutableTileMapRenderer(new byte[16_384]);
        byte[] current = new byte[16_384]; current[129] = 42;
        renderer.setPixels(current);
        MapCanvas canvas = mock(MapCanvas.class);

        renderer.render(null, canvas, null);

        verify(canvas).setPixel(1, 1, (byte) 42);
    }
}
