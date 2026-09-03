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
        // 26.2 原版 Projection.getMatrix 使用反转深度：near 传 depthFar、far 传 zNear，并按设备选择 [0,1] 深度。
        state.projectionMatrix.setPerspective(
                (float) Math.toRadians(fov / zoom),
                aspect,
                state.depthFar,
                Camera.PROJECTION_Z_NEAR,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
        ).rotateZ(roll);
    }
}
