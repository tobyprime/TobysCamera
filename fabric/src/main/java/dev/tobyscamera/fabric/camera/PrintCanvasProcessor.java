package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class PrintCanvasProcessor {
    public BufferedImage process(BufferedImage source, PrintLayout layout) {
        return process(source, layout.pixelWidth(), layout.pixelHeight(), layout.aspectRatio());
    }

    public BufferedImage preview(BufferedImage source, AspectRatio aspectRatio) {
        int width = 512;
        int height = (int) Math.round(width / aspectRatio.value());
        if (height > 512) {
            height = 512;
            width = (int) Math.round(height * aspectRatio.value());
        }
        return process(source, width, height, aspectRatio);
    }

    private BufferedImage process(BufferedImage source, int targetWidth, int targetHeight, AspectRatio aspectRatio) {
        if (source.getWidth() < 1 || source.getHeight() < 1) throw new IllegalArgumentException("source image must be non-empty");
        int compositionWidth = targetWidth;
        int compositionHeight = (int) Math.round(compositionWidth / aspectRatio.value());
        if (compositionHeight > targetHeight) {
            compositionHeight = targetHeight;
            compositionWidth = (int) Math.round(compositionHeight * aspectRatio.value());
        }
        double scale = Math.min((double) compositionWidth / source.getWidth(), (double) compositionHeight / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, (targetWidth - width) / 2, (targetHeight - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
