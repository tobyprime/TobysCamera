package dev.tobyscamera.fabric.camera;

/** Immutable upload progress measured in protocol chunks. */
public record UploadProgress(int completedChunks, int totalChunks) {
    public static final UploadProgress NONE = new UploadProgress(0, 0);
    public UploadProgress { if (completedChunks < 0 || totalChunks < 0) throw new IllegalArgumentException("chunk counts must be positive"); }
    public double fraction() { return totalChunks == 0 ? 0.0 : Math.clamp((double) completedChunks / totalChunks, 0.0, 1.0); }
    public int percentage() { return (int) Math.round(fraction() * 100.0); }
}
