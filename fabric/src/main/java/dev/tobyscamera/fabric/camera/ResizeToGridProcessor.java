package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class ResizeToGridProcessor {
    public CapturedFrame process(CapturedFrame frame) {
        int size = frame.gridSize() * 128;
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(frame.image(), 0, 0, size, size, null);
        graphics.dispose();
        return new CapturedFrame(output, frame.gridSize());
    }
}
