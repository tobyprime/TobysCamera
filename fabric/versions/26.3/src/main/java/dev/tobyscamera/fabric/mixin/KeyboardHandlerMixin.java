package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.KeyboardHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void tobyscamera$closeViewfinderOnEscape(long handle, int action, KeyEvent event, CallbackInfo callback) {
        if (action == InputConstants.PRESS && event.key() == InputConstants.KEY_ESCAPE && TobysCameraClient.closeViewfinder()) {
            callback.cancel();
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void tobyscamera$takePhotoOnConfiguredKey(long handle, int action, KeyEvent event, CallbackInfo callback) {
        TobysCameraClient.logViewfinderKeyEvent(action, event);
        if (action == InputConstants.PRESS && TobysCameraClient.handleShutterKey(event)) {
            callback.cancel();
        }
    }
}
