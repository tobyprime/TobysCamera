package dev.tobyscamera.fabric.camera;

public record AspectRatio(int width, int height) {
    public AspectRatio {
        if (width < 1 || height < 1) throw new IllegalArgumentException("ratio dimensions must be positive");
    }

    public static AspectRatio of(int width, int height) { return new AspectRatio(width, height); }

    public static AspectRatio parse(String value) {
        String[] parts = value.trim().split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("ratio must use width:height");
        try {
            return new AspectRatio(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ratio must use integer dimensions", exception);
        }
    }

    public double value() { return (double) width / height; }

    @Override public String toString() { return width + ":" + height; }
}
