package dev.tobyscamera.fabric.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow private float fov;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tobyscamera$applyViewfinderProjection(CameraRenderState state, float partialTick, CallbackInfo callback) {
        float zoom = TobysCameraClient.viewfinderZoom();
        float roll = TobysCameraClient.viewfinderRollRadians();
        if (zoom == 1.0f && roll == 0.0f) return;
        var window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / window.getHeight();
        // 26.1 原版仍为常规深度序，但矩阵需按设备选择 [0,1]（D3D/Metal）或 [-1,1]（GL）深度范围。
        state.projectionMatrix.setPerspective(
                (float) Math.toRadians(fov / zoom),
                aspect,
                Camera.PROJECTION_Z_NEAR,
                state.depthFar,
                RenderSystem.getDevice().isZZeroToOne()
        ).rotateZ(roll);
    }
}
