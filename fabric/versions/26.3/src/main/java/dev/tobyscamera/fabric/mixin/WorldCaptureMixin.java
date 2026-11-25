package dev.tobyscamera.fabric.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class WorldCaptureMixin {
    // 26.3 中 renderLevel() 无参，手部改由 render3dHud() 在 renderLevel 尾部绘制，
    // 因此 renderLevel 尾部截屏会把手截进去。取景界面打开期间直接跳过手部渲染，
    // 既让取景不被手持物遮挡，也保证截屏是干净的世界画面。
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void tobyscamera$hideHandsInViewfinder(CameraRenderState cameraState, PlayerRenderState playerState,
            GpuTextureView depthTexture, CallbackInfo callback) {
        if (TobysCameraClient.hideFirstPersonHands()) callback.cancel();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void tobyscamera$captureWorldBeforeHand(CallbackInfo callback) {
        TobysCameraClient.captureWorldBeforeHand(Minecraft.getInstance());
    }
}
