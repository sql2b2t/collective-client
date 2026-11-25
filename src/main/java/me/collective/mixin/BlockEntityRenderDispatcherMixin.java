package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.ESPModule;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
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

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private <E extends BlockEntity> void suppressBlockEntitySubmission(
      E blockEntity, float partialTick, PoseStack poses, MultiBufferSource bufferSource, CallbackInfo callback
   ) {
      if (!espModule().isRenderingBlockEntities()) {
         if (noRenderModule().shouldHideBlockEntityType(
            blockEntity.getType()
         )) {
            callback.cancel();
         } else {
            if (noRenderModule().isToggled()
               && noRenderModule().tileEntitiesSetting.getValue()
               && blockEntity.getBlockPos().distSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getBlockPosition())
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
