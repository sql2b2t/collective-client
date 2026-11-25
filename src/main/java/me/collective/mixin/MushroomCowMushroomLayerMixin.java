package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.render.EntityChamsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.MushroomCowMushroomLayer;
import net.minecraft.client.renderer.entity.state.MushroomCowRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MushroomCowMushroomLayer.class)
public class MushroomCowMushroomLayerMixin {
   @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/MushroomCowRenderState;FF)V", at = @At("HEAD"), cancellable = true)
   private void suppressMushroomsDuringChams(
      PoseStack poses, MultiBufferSource bufferSource, int packedLight, MushroomCowRenderState renderState, float ignoredRenderFloat1, float ignoredRenderFloat2, CallbackInfo callback
   ) {
      if (EntityChamsRenderer.isRenderingEntity()) {
         callback.cancel();
      }
   }
}
