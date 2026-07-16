package dev.tobyscamera.fabric.camera;

import java.awt.image.BufferedImage;

public record CapturedFrame(BufferedImage image, int gridSize, CameraComposition composition) {
    public CapturedFrame(BufferedImage image, int gridSize) { this(image, gridSize, CameraComposition.DEFAULT); }
    public CapturedFrame {
        if (image == null) throw new IllegalArgumentException("image is required");
        if (gridSize < 1 || gridSize > 4) throw new IllegalArgumentException("grid size must be 1..4");
        if (composition == null) throw new IllegalArgumentException("composition is required");
    }
}
