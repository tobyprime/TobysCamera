package dev.tobyscamera.fabric.camera;

public final class NativePixelFormat {
    private NativePixelFormat() { }

    public static int toArgb(int abgr) {
        return (abgr & 0xff00ff00) | ((abgr & 0x000000ff) << 16) | ((abgr >>> 16) & 0x000000ff);
    }

    public static int toAbgr(int argb) { return toArgb(argb); }
}
