package dev.tobyscamera.fabric.camera;

public record PrintLayout(int gridWidth, int gridHeight, AspectRatio aspectRatio) {
    public PrintLayout {
        if (gridWidth < 1 || gridHeight < 1) throw new IllegalArgumentException("grid dimensions must be positive");
    }

    public static PrintLayout forMaximumSide(int maximumSide, AspectRatio aspectRatio) {
        if (maximumSide < 1) throw new IllegalArgumentException("print size must be positive");
        int bestWidth = 1;
        int bestHeight = 1;
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestArea = 0;
        for (int width = 1; width <= maximumSide; width++) {
            for (int height = 1; height <= maximumSide; height++) {
                if (Math.max(width, height) != maximumSide) continue;
                double distance = Math.abs(Math.log(((double) width / height) / aspectRatio.value()));
                int area = width * height;
                if (distance < bestDistance || (Double.compare(distance, bestDistance) == 0 && area > bestArea)) {
                    bestWidth = width;
                    bestHeight = height;
                    bestDistance = distance;
                    bestArea = area;
                }
            }
        }
        return new PrintLayout(bestWidth, bestHeight, aspectRatio);
    }

    public int pixelWidth() { return gridWidth * 128; }
    public int pixelHeight() { return gridHeight * 128; }
}
