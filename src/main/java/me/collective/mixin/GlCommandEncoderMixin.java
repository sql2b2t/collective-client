package me.collective.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import me.collective.client.managers.RenderStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlCommandEncoder.class)
public class GlCommandEncoderMixin {

   @Inject(method = "drawFromBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"))
   private void applyRenderStateBeforeIndexedDraw(
      GlRenderPass renderPass, int ignoredDrawInt1, int ignoredDrawInt2, int ignoredDrawInt3, IndexType indexType, GlRenderPipeline pipeline, int ignoredDrawInt4, CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "drawFromBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawArrays(III)V"))
   private void applyRenderStateBeforeArrayDraw(
      GlRenderPass renderPass, int ignoredDrawInt1, int ignoredDrawInt2, int ignoredDrawInt3, IndexType indexType, GlRenderPipeline pipeline, int ignoredDrawInt4, CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "drawFromBuffers", at = @At("TAIL"))
   private void restoreRenderStateAfterDraw(
      GlRenderPass renderPass, int ignoredDrawInt1, int ignoredDrawInt2, int ignoredDrawInt3, IndexType indexType, GlRenderPipeline pipeline, int ignoredDrawInt4, CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(false);
   }
}
