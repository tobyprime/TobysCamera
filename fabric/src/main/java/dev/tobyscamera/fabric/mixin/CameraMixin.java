package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
abstract class CameraMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void tobyscamera$applyViewfinderZoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(callback.getReturnValueF() / TobysCameraClient.viewfinderZoom());
    }

    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void tobyscamera$applyViewfinderRoll(float fov, CallbackInfoReturnable<Matrix4f> callback) {
        float roll = TobysCameraClient.viewfinderRollRadians();
        if (roll != 0.0f) callback.setReturnValue(callback.getReturnValue().rotateZ(roll));
    }

    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V")
    )
    private void tobyscamera$captureWorldBeforeHand(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo callback) {
        TobysCameraClient.captureWorldBeforeHand(net.minecraft.client.Minecraft.getInstance());
    }
}
