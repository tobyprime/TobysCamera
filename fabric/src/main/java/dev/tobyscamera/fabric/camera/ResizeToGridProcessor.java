package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class ResizeToGridProcessor {
    public CapturedFrame process(CapturedFrame frame) {
        int maximumSide = frame.gridSize() * 128;
        double scale = (double) maximumSide / Math.max(frame.image().getWidth(), frame.image().getHeight());
        int width = (int) Math.round(frame.image().getWidth() * scale);
        int height = (int) Math.round(frame.image().getHeight() * scale);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(frame.image(), 0, 0, width, height, null);
        graphics.dispose();
        return new CapturedFrame(output, frame.gridSize(), frame.composition());
    }
}
