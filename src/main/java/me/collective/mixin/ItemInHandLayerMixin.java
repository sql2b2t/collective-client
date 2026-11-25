package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.render.EntityChamsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
   @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
   private void suppressHeldItemDuringChams(
      ArmedEntityRenderState entityState, ItemStackRenderState itemState, HumanoidArm arm, PoseStack poses, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback
   ) {
      if (EntityChamsRenderer.isRenderingEntity()) {
         callback.cancel();
      }
   }
}
