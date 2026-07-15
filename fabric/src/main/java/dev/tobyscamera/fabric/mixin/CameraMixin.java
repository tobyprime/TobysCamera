package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class CameraMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void tobyscamera$applyViewfinderZoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(callback.getReturnValueF() / TobysCameraClient.viewfinderZoom());
    }
}
