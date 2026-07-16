package dev.tobyscamera.fabric.camera;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class PrintCanvasProcessor {
    public BufferedImage process(BufferedImage source, PrintLayout layout) {
        if (source.getWidth() < 1 || source.getHeight() < 1) throw new IllegalArgumentException("source image must be non-empty");
        int targetWidth = layout.pixelWidth();
        int targetHeight = layout.pixelHeight();
        double scale = Math.min((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, (targetWidth - width) / 2, (targetHeight - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
