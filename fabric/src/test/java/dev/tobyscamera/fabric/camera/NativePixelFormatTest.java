package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativePixelFormatTest {
    @Test
    void roundTripsArgbAndNativeAbgr() {
        int argb = 0x7f1234ab;
        assertEquals(argb, NativePixelFormat.toArgb(NativePixelFormat.toAbgr(argb)));
    }

    @Test
    void preservesRedAndYellowReturnedByNativeImageGetPixel() {
        assertEquals(0xffff0000, NativePixelFormat.toArgb(0xffff0000));
        assertEquals(0xffffff00, NativePixelFormat.toArgb(0xffffff00));
        assertEquals(0xffff0000, NativePixelFormat.toAbgr(0xffff0000));
        assertEquals(0xffffff00, NativePixelFormat.toAbgr(0xffffff00));
    }
}
