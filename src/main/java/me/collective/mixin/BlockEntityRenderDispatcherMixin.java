package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.ESPModule;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
   private static ESPModule espModule;
   private static NoRenderModule noRenderModule;
   private static ESPModule espModule() {
      if (espModule == null) {
         espModule = ClientContext.getModule(ESPModule.class);
      }
      return espModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
   private void suppressBlockEntitySubmission(
      BlockEntityRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      if (!espModule().isRenderingBlockEntities()) {
         if (noRenderModule().shouldHideBlockEntityType(
            renderState.blockEntityType
         )) {
            callback.cancel();
         } else {
            if (noRenderModule().isToggled()
               && noRenderModule().tileEntitiesSetting.getValue()
               && renderState.blockPos.distSqr(Minecraft.getInstance().gameRenderer.getMainCamera().blockPosition())
                  > noRenderModule().tileDistanceSetting
                        .getValue()
                     * noRenderModule().tileDistanceSetting
                        .getValue()) {
               callback.cancel();
            }
         }
      }
   }

}
