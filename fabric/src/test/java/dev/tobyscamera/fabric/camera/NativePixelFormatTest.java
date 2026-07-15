package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativePixelFormatTest {
    @Test
    void roundTripsArgbAndNativeAbgr() {
        int argb = 0x7f1234ab;
        assertEquals(argb, NativePixelFormat.toArgb(NativePixelFormat.toAbgr(argb)));
    }
}
