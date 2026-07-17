package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ViewfinderSettingsStoreTest {
    @Test
    void persistsTheEntireViewfinderConfigurationAcrossClientRestarts() throws Exception {
        Path file = Files.createTempDirectory("tobyscamera-settings").resolve("viewfinder.properties");
        ViewfinderSettings expected = new ViewfinderSettings(CompositionGrid.CROSSHAIR, 2.75f,
                new CameraComposition(AspectRatio.of(3, 2), 123.0f), CaptureMode.VIDEO, 5);

        new ViewfinderSettingsStore(file).save(expected);

        assertEquals(expected, new ViewfinderSettingsStore(file).load());
    }
}
