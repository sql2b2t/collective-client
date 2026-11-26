package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.events.EventStage;
import me.collective.client.events.ModelFeatureRendererRenderModelEvent;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
   @Inject(method = "buildGroup", at = @At("HEAD"))
   private void postBeforeEntityModelRender(
      FeatureFrameContext frameContext, List<Submit<?>> submissions, CallbackInfo callback
   ) {
      for (Submit<?> submission : submissions) {
         if (submission.model() instanceof EntityModel<?> entityModel) {
            ModelFeatureRendererRenderModelEvent event = new ModelFeatureRendererRenderModelEvent(entityModel, EventStage.Pre);
            ClientAccess.eventBus.post(event);
         }
      }
   }

   @Inject(method = "buildGroup", at = @At("RETURN"))
   private void postAfterEntityModelRender(
      FeatureFrameContext frameContext, List<Submit<?>> submissions, CallbackInfo callback
   ) {
      for (Submit<?> submission : submissions) {
         if (submission.model() instanceof EntityModel<?> entityModel) {
            ModelFeatureRendererRenderModelEvent event = new ModelFeatureRendererRenderModelEvent(entityModel, EventStage.Post);
            ClientAccess.eventBus.post(event);
         }
      }
   }
}
