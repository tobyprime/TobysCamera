package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    // 缩放走原版望远镜链路（见 PlayerMixin），不在此处改投影。
    // roll 本质需要旋转矩阵；保留在 extractRenderState 尾部是因为 Iris 与 Voxy
    // 捕获的都是这份 cameraState.projectionMatrix，两条管线的旋转保持一致。
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tobyscamera$applyViewfinderRoll(CameraRenderState state, float partialTick, CallbackInfo callback) {
        float roll = TobysCameraClient.viewfinderRollRadians();
        if (roll == 0.0f) return;
        state.projectionMatrix.rotateZ(roll);
    }
}
