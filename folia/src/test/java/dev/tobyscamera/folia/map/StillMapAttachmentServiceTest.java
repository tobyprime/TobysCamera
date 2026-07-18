package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StillMapAttachmentServiceTest {
    private static final MediaMapDescriptor DESCRIPTOR = new MediaMapDescriptor.PhotoTile(41,
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), new dev.tobyscamera.folia.storage.TileCoordinate(0, 0));

    @Test
    void sharesOneAsyncReadUntilTheFinalActiveSourceIsRemoved() {
        MapView map = mock(MapView.class);
        List<Runnable> async = new ArrayList<>();
        List<Runnable> sync = new ArrayList<>();
        StillMapAttachmentService service = new StillMapAttachmentService(id -> id == 41 ? map : null,
                async::add, sync::add, ignored -> { });

        service.attach("main-hand", DESCRIPTOR, () -> pixels((byte) 42));
        service.attach("frame", DESCRIPTOR, () -> pixels((byte) 42));

        assertEquals(1, async.size());
        verify(map).addRenderer(any(TileMapRenderer.class));
        async.removeFirst().run();
        assertEquals(1, sync.size());
        sync.removeFirst().run();

        service.detach("main-hand");
        verify(map, never()).removeRenderer(any(TileMapRenderer.class));
        service.detach("frame");
        ArgumentCaptor<MapRenderer> removed = ArgumentCaptor.forClass(MapRenderer.class);
        verify(map).removeRenderer(removed.capture());
        renderIsBlank((TileMapRenderer) removed.getValue());
    }

    @Test
    void ignoresAnAsyncResultAfterItsLastSourceIsRemoved() {
        MapView map = mock(MapView.class);
        List<Runnable> async = new ArrayList<>();
        List<Runnable> sync = new ArrayList<>();
        StillMapAttachmentService service = new StillMapAttachmentService(id -> map, async::add, sync::add, ignored -> { });

        service.attach("main-hand", DESCRIPTOR, () -> pixels((byte) 42));
        service.detach("main-hand");
        async.removeFirst().run();
        sync.removeFirst().run();

        ArgumentCaptor<MapRenderer> removed = ArgumentCaptor.forClass(MapRenderer.class);
        verify(map).removeRenderer(removed.capture());
        renderIsBlank((TileMapRenderer) removed.getValue());
    }

    private static void renderIsBlank(TileMapRenderer renderer) {
        MapCanvas canvas = mock(MapCanvas.class);
        renderer.render(null, canvas, null);
        verify(canvas).setPixel(1, 1, (byte) 0);
    }

    private static byte[] pixels(byte value) {
        byte[] pixels = new byte[16_384];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
