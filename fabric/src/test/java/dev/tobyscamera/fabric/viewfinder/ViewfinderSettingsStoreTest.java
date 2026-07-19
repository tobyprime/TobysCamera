package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewfinderSettingsStoreTest {
    @Test
    void savesAndLoadsTheSelectedPrintSize(@TempDir Path directory) throws Exception {
        ViewfinderSettings settings = new ViewfinderSettings(CompositionGrid.THIRDS, 2.0f,
                new CameraComposition(AspectRatio.of(3, 2), 10.0f), 4);
        ViewfinderSettingsStore store = new ViewfinderSettingsStore(directory.resolve("viewfinder.properties"));

        store.save(settings);

        assertEquals(settings, store.load());
    }
}
