package dev.tobyscamera.fabric.camera;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class CenterSquareCropProcessor {
    public CapturedFrame process(CapturedFrame frame) {
        BufferedImage source = frame.image();
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, size, size, x, y, x + size, y + size, null);
        graphics.dispose();
        return new CapturedFrame(output, frame.gridSize());
    }
}
