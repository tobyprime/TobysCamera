package dev.tobyscamera.fabric.camera;

import dev.tobyscamera.fabric.viewfinder.ViewfinderState;

public final class PhotoRenderPolicy {
    private PhotoRenderPolicy() { }

    public static boolean hideNameTags(ViewfinderState state, boolean captureReady) {
        return state == ViewfinderState.CAPTURING && captureReady;
    }

    /** 取景界面一打开就收起第一人称手部，取景不被手持物遮挡，拍照也不会把手截进去。 */
    public static boolean hideFirstPersonHands(ViewfinderState state) {
        return state != ViewfinderState.CLOSED;
    }
}
