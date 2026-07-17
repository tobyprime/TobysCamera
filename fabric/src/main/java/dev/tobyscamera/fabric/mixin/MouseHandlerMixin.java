package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void tobyscamera$takePhotoOnConfiguredMouseButton(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS && TobysCameraClient.handleShutterMouse(new MouseButtonEvent(0.0, 0.0, button))) {
            callback.cancel();
        }
    }
}
