package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.XrayModule;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
   private static XrayModule xrayModule;
   private static NoRenderModule noRenderModule;
   private static XrayModule xrayModule() {
      if (xrayModule == null) {
         xrayModule = ClientContext.getModule(XrayModule.class);
      }
      return xrayModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Inject(method = {"tesselateWithAO", "tesselateWithoutAO"}, at = @At("HEAD"), cancellable = true)
   private void suppressHiddenBlockEntityMesh(
      BlockAndTintGetter level, List parts, BlockState blockState, BlockPos blockPos, PoseStack poses, VertexConsumer vertices, boolean checkSides, int packedOverlay, CallbackInfo callback
   ) {
      if (XrayModule.getInstance()
            .isToggled()
         && !XrayModule.getInstance()
            .shouldRenderBlock(
               blockState
            )
         && XrayModule.getInstance()
            .shouldHideUnselectedBlocks()) {
         callback.cancel();
      } else {
         if (noRenderModule().shouldHideBlockEntity(blockState)) {
            callback.cancel();
         }
      }
   }

   @ModifyArgs(
      method = "putQuadData",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V")
   )
   private void applyXrayQuadAlpha(
      Args arguments, @Local(argsOnly = true) BlockState blockState
   ) {
      if (xrayModule().isToggled()
         && !xrayModule().alphaSetting
            .isAtMinimum()
         && !xrayModule().alphaSetting
            .isAtMaximum()
         && !xrayModule().shouldRenderBlock(
            blockState
         )) {
         arguments.set(
            6,
            (xrayModule().alphaSetting.getValue()).intValue()
               / 100.0F
         );
      }
   }
}
