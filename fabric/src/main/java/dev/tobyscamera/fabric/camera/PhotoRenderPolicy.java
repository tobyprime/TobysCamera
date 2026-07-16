package dev.tobyscamera.fabric.camera;

import dev.tobyscamera.fabric.viewfinder.ViewfinderState;

public final class PhotoRenderPolicy {
    private PhotoRenderPolicy() { }

    public static boolean hideNameTags(ViewfinderState state, boolean captureReady) {
        return state == ViewfinderState.CAPTURING && captureReady;
    }
}
