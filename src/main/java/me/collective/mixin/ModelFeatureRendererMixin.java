package me.collective.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.collective.client.ClientAccess;
import me.collective.client.events.EventStage;
import me.collective.client.events.ModelFeatureRendererRenderModelEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
   @Inject(method = "renderModel", at = @At("HEAD"))
   private void postBeforeEntityModelRender(
      ModelSubmit submission, RenderType renderType, VertexConsumer vertices, OutlineBufferSource outlineBuffers, BufferSource buffers, CallbackInfo callback
   ) {
      if (submission.model() instanceof EntityModel entityModel) {
         ModelFeatureRendererRenderModelEvent event = new ModelFeatureRendererRenderModelEvent(entityModel, EventStage.Pre);
         ClientAccess.eventBus
            .post(event);
      }
   }

   @Inject(method = "renderModel", at = @At("RETURN"))
   private void postAfterEntityModelRender(
      ModelSubmit submission, RenderType renderType, VertexConsumer vertices, OutlineBufferSource outlineBuffers, BufferSource buffers, CallbackInfo callback
   ) {
      if (submission.model() instanceof EntityModel entityModel) {
         ModelFeatureRendererRenderModelEvent event = new ModelFeatureRendererRenderModelEvent(entityModel, EventStage.Post);
         ClientAccess.eventBus
            .post(event);
      }
   }
}
