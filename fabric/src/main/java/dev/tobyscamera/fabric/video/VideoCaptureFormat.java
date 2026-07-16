package dev.tobyscamera.fabric.video;

import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.PrintLayout;

/** Immutable capture plan that keeps video readback and temporary PNGs no larger than the camera can print. */
public record VideoCaptureFormat(AspectRatio aspectRatio, int screenshotDownscale, int cropLeft, int cropTop, int cropWidth, int cropHeight,
        int outputWidth, int outputHeight) {
    public static VideoCaptureFormat forCamera(int maximumGridSize, AspectRatio aspectRatio, int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth < 1 || framebufferHeight < 1) throw new IllegalArgumentException("framebuffer dimensions must be positive");
        PrintLayout layout = PrintLayout.forMaximumSide(maximumGridSize, aspectRatio);
        int canvasWidth = layout.pixelWidth(), canvasHeight = layout.pixelHeight();
        int outputWidth = canvasWidth;
        int outputHeight = (int) Math.round(outputWidth / aspectRatio.value());
        if (outputHeight > canvasHeight) {
            outputHeight = canvasHeight;
            outputWidth = (int) Math.round(outputHeight * aspectRatio.value());
        }
        int downscale = largestCompatibleDownscale(framebufferWidth, framebufferHeight, outputWidth, outputHeight);
        int width = framebufferWidth / downscale, height = framebufferHeight / downscale;
        int cropWidth = width;
        int cropHeight = (int) Math.round(cropWidth / aspectRatio.value());
        if (cropHeight > height) {
            cropHeight = height;
            cropWidth = (int) Math.round(cropHeight * aspectRatio.value());
        }
        return new VideoCaptureFormat(aspectRatio, downscale, (width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight, outputWidth, outputHeight);
    }

    private static int largestCompatibleDownscale(int width, int height, int outputWidth, int outputHeight) {
        int maximum = Math.min(width / outputWidth, height / outputHeight);
        for (int candidate = maximum; candidate > 1; candidate--) if (width % candidate == 0 && height % candidate == 0) return candidate;
        return 1;
    }
}
