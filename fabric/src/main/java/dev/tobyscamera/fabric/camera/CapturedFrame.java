package dev.tobyscamera.fabric.camera;

import java.awt.image.BufferedImage;

public record CapturedFrame(BufferedImage image, int gridSize, CameraComposition composition) {
    public CapturedFrame(BufferedImage image, int gridSize) { this(image, gridSize, CameraComposition.DEFAULT); }
    public CapturedFrame {
        if (image == null) throw new IllegalArgumentException("image is required");
        if (gridSize < 1) throw new IllegalArgumentException("grid size must be positive");
        if (composition == null) throw new IllegalArgumentException("composition is required");
    }
}
