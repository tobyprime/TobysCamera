package dev.tobyscamera.fabric.camera;

import java.awt.image.BufferedImage;

/** CPU-only preparation for a photo confirmation preview; safe to execute off the render thread. */
public final class PhotoPreviewProcessor {
    private final MapTileEncoder encoder = new MapTileEncoder();

    public Result process(CapturedFrame frame, int printSize, MapTileEncoder.DitheringMode dithering) {
        PrintCanvasProcessor canvasProcessor = new PrintCanvasProcessor();
        BufferedImage canvas = canvasProcessor.process(frame.image(), PrintLayout.forMaximumSide(printSize, frame.composition().aspectRatio()));
        MapTileEncoder.EncodedPhoto photo = encoder.encode(canvas, dithering);
        BufferedImage bagCanvas = canvasProcessor.process(frame.image(), PrintLayout.forMaximumSide(1, frame.composition().aspectRatio()));
        return new Result(photo, encoder.palettePreview(photo), encoder.bagPreview(bagCanvas, dithering));
    }

    public record Result(MapTileEncoder.EncodedPhoto photo, BufferedImage image, byte[] bagPreview) { }
}
