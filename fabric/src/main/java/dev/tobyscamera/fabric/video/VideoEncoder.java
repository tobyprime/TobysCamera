package dev.tobyscamera.fabric.video;

import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.PrintCanvasProcessor;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Converts only retained disk-backed frames to the exact palette tiles that will be uploaded. */
public final class VideoEncoder {
    private final TemporaryVideoRecording recording;
    private final VideoFrameRange range;
    private final PrintLayout layout;
    private final MapTileEncoder.DitheringMode dithering;
    private final Map<Integer, MapTileEncoder.EncodedPhoto> encoded = new HashMap<>();
    private final MapTileEncoder tileEncoder = new MapTileEncoder();
    private final PrintCanvasProcessor canvasProcessor = new PrintCanvasProcessor();

    public VideoEncoder(TemporaryVideoRecording recording, VideoFrameRange range, PrintLayout layout, MapTileEncoder.DitheringMode dithering) {
        this.recording = recording; this.range = range; this.layout = layout; this.dithering = dithering;
    }

    public int frameCount() { return range.count(); }
    public int gridWidth() { return layout.gridWidth(); }
    public int gridHeight() { return layout.gridHeight(); }

    public MapTileEncoder.EncodedPhoto frame(int retainedIndex) throws IOException {
        if (retainedIndex < 0 || retainedIndex >= frameCount()) throw new IndexOutOfBoundsException(retainedIndex);
        MapTileEncoder.EncodedPhoto existing = encoded.get(retainedIndex);
        if (existing != null) return existing;
        var processed = canvasProcessor.process(recording.read(range.startInclusive() + retainedIndex), layout);
        var result = tileEncoder.encode(processed, dithering);
        encoded.put(retainedIndex, result);
        return result;
    }
}
