package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.render.EntityChamsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {
   @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
   private void suppressHeadLayerDuringChams(
      PoseStack poses, MultiBufferSource bufferSource, int packedLight, LivingEntityRenderState renderState, float ignoredRenderFloat1, float ignoredRenderFloat2, CallbackInfo callback
   ) {
      if (EntityChamsRenderer.isRenderingEntity()) {
         callback.cancel();
      }
   }
}
