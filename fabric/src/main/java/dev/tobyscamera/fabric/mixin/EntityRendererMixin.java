package dev.tobyscamera.fabric.mixin;

import dev.tobyscamera.fabric.TobysCameraClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void tobyscamera$hideNameTagsForPhoto(Entity entity, EntityRenderState state, float partialTick, CallbackInfo callback) {
        if (!TobysCameraClient.hideNameTagsForPhoto()) return;
        state.nameTag = null;
    }
}
