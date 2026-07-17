package dev.tobyscamera.fabric.viewfinder;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.CameraComposition;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small, dependency-free client settings file. Invalid or missing files safely fall back to defaults. */
public final class ViewfinderSettingsStore {
    private final Path file;

    public ViewfinderSettingsStore(Path file) { this.file = file; }

    public ViewfinderSettings load() {
        if (!Files.isRegularFile(file)) return ViewfinderSettings.DEFAULT;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            return new ViewfinderSettings(
                    CompositionGrid.valueOf(properties.getProperty("grid")),
                    Float.parseFloat(properties.getProperty("zoom")),
                    new CameraComposition(AspectRatio.of(Integer.parseInt(properties.getProperty("ratio_width")),
                            Integer.parseInt(properties.getProperty("ratio_height"))), Float.parseFloat(properties.getProperty("roll"))),
                    CaptureMode.valueOf(properties.getProperty("mode")), Integer.parseInt(properties.getProperty("video_fps")));
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            return ViewfinderSettings.DEFAULT;
        }
    }

    public void save(ViewfinderSettings settings) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("grid", settings.grid().name());
        properties.setProperty("zoom", Float.toString(settings.zoom()));
        properties.setProperty("ratio_width", Integer.toString(settings.composition().aspectRatio().width()));
        properties.setProperty("ratio_height", Integer.toString(settings.composition().aspectRatio().height()));
        properties.setProperty("roll", Float.toString(settings.composition().rollDegrees()));
        properties.setProperty("mode", settings.mode().name());
        properties.setProperty("video_fps", Integer.toString(settings.videoFps()));
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "TobysCamera client settings"); }
    }
}
