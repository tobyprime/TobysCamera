package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.fabric.viewfinder.ViewfinderState;
import org.junit.jupiter.api.Test;

class PhotoRenderPolicyTest {
    @Test
    void hidesNameTagsOnlyDuringTheReadyCaptureFrame() {
        assertFalse(PhotoRenderPolicy.hideNameTags(ViewfinderState.VIEWFINDER, true));
        assertFalse(PhotoRenderPolicy.hideNameTags(ViewfinderState.CAPTURING, false));
        assertTrue(PhotoRenderPolicy.hideNameTags(ViewfinderState.CAPTURING, true));
        assertFalse(PhotoRenderPolicy.hideNameTags(ViewfinderState.PREVIEW, true));
    }
}
