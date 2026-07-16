package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class CenterSquareCropProcessor {
    public CapturedFrame process(CapturedFrame frame) {
        BufferedImage source = frame.image();
        double ratio = frame.composition().aspectRatio().value();
        int width = source.getWidth();
        int height = (int) Math.round(width / ratio);
        if (height > source.getHeight()) {
            height = source.getHeight();
            width = (int) Math.round(height * ratio);
        }
        int x = (source.getWidth() - width) / 2;
        int y = (source.getHeight() - height) / 2;
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        graphics.dispose();
        return new CapturedFrame(output, frame.gridSize(), frame.composition());
    }
}
