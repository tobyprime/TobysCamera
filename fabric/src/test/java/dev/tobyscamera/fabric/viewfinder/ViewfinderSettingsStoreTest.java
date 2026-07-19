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
                new CameraComposition(AspectRatio.of(3, 2), 10.0f), 4, false, true, false);
        ViewfinderSettingsStore store = new ViewfinderSettingsStore(directory.resolve("viewfinder.properties"));

        store.save(settings);

        assertEquals(settings, store.load());
    }

    @Test
    void missingVisibilityPreferencesDefaultToPublic(@TempDir Path directory) throws Exception {
        ViewfinderSettingsStore store = new ViewfinderSettingsStore(directory.resolve("viewfinder.properties"));
        java.nio.file.Files.writeString(directory.resolve("viewfinder.properties"), "grid=NONE\nzoom=1\nratio_width=1\nratio_height=1\nroll=0\nprint_size=1\n");

        ViewfinderSettings settings = store.load();

        assertEquals(true, settings.publicAddress());
        assertEquals(true, settings.publicPhotographer());
        assertEquals(true, settings.publicCapturedTime());
    }
}
