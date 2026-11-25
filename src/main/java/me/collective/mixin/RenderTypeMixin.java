package me.collective.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.collective.client.ClientAccess;
import me.collective.client.events.EventStage;
import me.collective.client.events.RenderTypeDrawEvent;
import me.collective.client.render.ClientRenderPipelines;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderTypeMixin {
   @Unique
   private Integer glintTextureOverride;
   @Unique
   private Boolean glintPipeline;

   @Unique
   private boolean isGlintPipeline() {
      if (this.glintPipeline == null) {
         this.glintPipeline = ClientRenderPipelines.isGlint((RenderType)(Object)this);
      }
      return this.glintPipeline;
   }

   @Inject(method = "draw", at = @At("HEAD"))
   private void captureGlintTextureOverride(CallbackInfo callback) {
      if (this.isGlintPipeline()) {
         RenderTypeDrawEvent event = new RenderTypeDrawEvent(EventStage.Pre);
         ClientAccess.eventBus.post(event);
         this.glintTextureOverride = event.getOverride();
      }
   }

   @Inject(
      method = "draw",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
         shift = Shift.BEFORE
      )
   )
   private void overrideGlintTexture(CallbackInfo callback) {
      if (this.isGlintPipeline() && this.glintTextureOverride != null) {
         RenderSystem.setShaderTexture(0, this.glintTextureOverride);
      }
   }

   @Inject(method = "draw", at = @At("TAIL"))
   private void postGlintDrawPostEvent(CallbackInfo callback) {
      if (this.isGlintPipeline()) {
         ClientAccess.eventBus.post(new RenderTypeDrawEvent(EventStage.Post));
         this.glintTextureOverride = null;
      }
   }
}
