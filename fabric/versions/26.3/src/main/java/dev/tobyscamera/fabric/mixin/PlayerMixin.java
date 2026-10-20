package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class PlayerMixin {
    // 与原版望远镜（isScoping → 0.1F）完全相同的缩放链路：fov 修饰符由 Camera.tickFov
    // 逐刻平滑过渡，vanilla/Iris/Voxy 全部从同一标量 fov 派生投影，不触碰任何矩阵。
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void tobyscamera$applyViewfinderZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> callback) {
        float zoom = TobysCameraClient.viewfinderZoom();
        if (zoom == 1.0f) return;
        callback.setReturnValue(callback.getReturnValueF() / zoom);
    }
}
