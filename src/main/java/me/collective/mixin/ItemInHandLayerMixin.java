package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.render.EntityChamsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
   @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
   private void suppressHeldItemDuringChams(
      ArmedEntityRenderState entityState, ItemStackRenderState itemState, ItemStack stack, HumanoidArm arm, PoseStack poses, SubmitNodeCollector collector, int packedLight, CallbackInfo callback
   ) {
      if (EntityChamsRenderer.isRenderingEntity()) {
         callback.cancel();
      }
   }
}
