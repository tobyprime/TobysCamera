package dev.tobyscamera.common.video;

/** The only rates that divide Minecraft's 20 ticks per second exactly. */
public final class VideoFrameRate {
    private static final int[] VALUES = {1, 5, 10, 20};
    private VideoFrameRate() { }

    public static boolean isSupported(int fps) { for (int value : VALUES) if (value == fps) return true; return false; }
    public static int clampToMaximum(int maximum) { int result = 1; for (int value : VALUES) if (value <= maximum) result = value; return result; }
    public static int next(int current, int direction, int maximum) {
        int capped = clampToMaximum(maximum); int index = 0;
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] <= capped && VALUES[i] <= current) index = i;
        if (direction > 0) while (index + 1 < VALUES.length && VALUES[index + 1] <= capped) index++;
        if (direction < 0 && index > 0) index--;
        return VALUES[index];
    }
}
