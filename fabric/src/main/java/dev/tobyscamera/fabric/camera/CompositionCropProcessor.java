package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Crops a screen capture to the aspect ratio selected when it was taken. */
public final class CompositionCropProcessor {
    public CapturedFrame process(CapturedFrame frame) {
        BufferedImage source = frame.image();
        double ratio = frame.composition().aspectRatio().value();
        int cropWidth = source.getWidth();
        int cropHeight = (int) Math.round(cropWidth / ratio);
        if (cropHeight > source.getHeight()) {
            cropHeight = source.getHeight();
            cropWidth = (int) Math.round(cropHeight * ratio);
        }
        int left = (source.getWidth() - cropWidth) / 2;
        int top = (source.getHeight() - cropHeight) / 2;
        BufferedImage output = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, cropWidth, cropHeight, left, top, left + cropWidth, top + cropHeight, null);
        } finally {
            graphics.dispose();
        }
        return new CapturedFrame(output, frame.gridSize(), frame.composition());
    }
}
