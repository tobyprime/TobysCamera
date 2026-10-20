package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class WorldCaptureMixin {
    // 26.3 中 renderLevel() 无参，且 renderItemInHand 已从该流程移除（手部改由
    // FirstPersonHandsAndItemsRenderer 在 renderLevel 之后的 GUI 阶段绘制），
    // 因此在 renderLevel 尾部截屏：世界已画完、手部尚未叠加。
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void tobyscamera$captureWorldBeforeHand(CallbackInfo callback) {
        TobysCameraClient.captureWorldBeforeHand(Minecraft.getInstance());
    }
}
