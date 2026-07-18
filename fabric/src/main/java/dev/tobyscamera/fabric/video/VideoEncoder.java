package dev.tobyscamera.fabric.video;

import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.CameraComposition;
import dev.tobyscamera.fabric.camera.CapturedFrame;
import dev.tobyscamera.fabric.camera.CompositionCropProcessor;
import dev.tobyscamera.fabric.camera.PrintCanvasProcessor;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.io.IOException;

/** Converts only retained disk-backed frames to the exact palette tiles that will be uploaded. */
public final class VideoEncoder {
    private final TemporaryVideoRecording recording;
    private final VideoFrameRange range;
    private final PrintLayout layout;
    private final MapTileEncoder.DitheringMode dithering;
    private int cachedFrameIndex = -1;
    private MapTileEncoder.EncodedPhoto cachedFrame;
    private int cachedPreviewFrameIndex = -1;
    private byte[] cachedPreview;
    private final MapTileEncoder tileEncoder = new MapTileEncoder();
    private final PrintCanvasProcessor canvasProcessor = new PrintCanvasProcessor();
    private final CompositionCropProcessor cropProcessor = new CompositionCropProcessor();

    public VideoEncoder(TemporaryVideoRecording recording, VideoFrameRange range, PrintLayout layout, MapTileEncoder.DitheringMode dithering) {
        this.recording = recording; this.range = range; this.layout = layout; this.dithering = dithering;
    }

    public int frameCount() { return range.count(); }
    public int gridWidth() { return layout.gridWidth(); }
    public int gridHeight() { return layout.gridHeight(); }

    public MapTileEncoder.EncodedPhoto frame(int retainedIndex) throws IOException {
        if (retainedIndex < 0 || retainedIndex >= frameCount()) throw new IndexOutOfBoundsException(retainedIndex);
        if (cachedFrameIndex == retainedIndex) return cachedFrame;
        var canvas = printCanvas(retainedIndex);
        var result = tileEncoder.encode(canvas, dithering);
        cachedFrameIndex = retainedIndex;
        cachedFrame = result;
        cachedPreviewFrameIndex = retainedIndex;
        cachedPreview = tileEncoder.bagPreview(canvas, dithering);
        return result;
    }

    /** Renders a retained source frame directly into the one-tile photo-bag preview. */
    public byte[] bagPreview(int retainedIndex) throws IOException {
        if (retainedIndex < 0 || retainedIndex >= frameCount()) throw new IndexOutOfBoundsException(retainedIndex);
        if (cachedPreviewFrameIndex != retainedIndex) {
            cachedPreview = tileEncoder.bagPreview(printCanvas(retainedIndex), dithering);
            cachedPreviewFrameIndex = retainedIndex;
        }
        return cachedPreview.clone();
    }

    private java.awt.image.BufferedImage printCanvas(int retainedIndex) throws IOException {
        if (retainedIndex < 0 || retainedIndex >= frameCount()) throw new IndexOutOfBoundsException(retainedIndex);
        var captured = new CapturedFrame(recording.read(range.startInclusive() + retainedIndex), 1,
                new CameraComposition(layout.aspectRatio(), 0.0f));
        var cropped = cropProcessor.process(captured).image();
        return canvasProcessor.process(cropped, layout);
    }
}
