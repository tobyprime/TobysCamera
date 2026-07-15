package dev.tobyscamera.fabric.input;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class CameraKeyCategoryTest {
    @Test
    void returnsOneSharedCategoryForAllCameraKeys() {
        assertSame(CameraKeyCategory.value(), CameraKeyCategory.value());
    }
}
