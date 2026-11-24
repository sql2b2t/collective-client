package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.collective.client.ClientAccess;
import me.collective.client.events.EventStage;
import me.collective.client.events.RenderTypeDrawEvent;
import me.collective.client.render.ClientRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderTypeMixin {
   @Unique
   private GpuTextureView glintTextureOverride;
   @Unique
   private boolean glintPipeline;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void captureGlintPipeline(
      String name, RenderSetup setup, CallbackInfo callback
   ) {
      this.glintPipeline = ClientRenderPipelines.isGlint(setup.pipeline);
   }

   @Inject(method = "draw", at = @At("HEAD"))
   private void captureGlintTextureOverride(CallbackInfo callback) {
      if (this.glintPipeline) {
         RenderTypeDrawEvent event = new RenderTypeDrawEvent(EventStage.Pre);
         ClientAccess.eventBus
            .post(event);
         this.glintTextureOverride = event.getOverride();
      }
   }

   @ModifyExpressionValue(
      method = "draw",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderSetup$TextureAndSampler;textureView()Lcom/mojang/blaze3d/textures/GpuTextureView;")
   )
   private GpuTextureView overrideGlintTexture(GpuTextureView originalTexture) {
      return this.glintPipeline
            && this.glintTextureOverride != null
         ? this.glintTextureOverride
         : originalTexture;
   }

   @Inject(method = "draw", at = @At("TAIL"))
   private void postGlintDrawPostEvent(CallbackInfo callback) {
      if (this.glintPipeline) {
         ClientAccess.eventBus
            .post(new RenderTypeDrawEvent(EventStage.Post));
      }
   }

}
