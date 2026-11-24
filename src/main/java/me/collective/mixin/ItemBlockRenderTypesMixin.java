package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.render.RenderUtils;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.XrayModule;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public class ItemBlockRenderTypesMixin {
   private static NoRenderModule noRenderModule;
   private static XrayModule xrayModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static XrayModule xrayModule() {
      if (xrayModule == null) {
         xrayModule = ClientContext.getModule(XrayModule.class);
      }
      return xrayModule;
   }

   @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
   private static void applyTranslucentHandRenderType(
      BlockState blockState, CallbackInfoReturnable<RenderType> callback
   ) {
      if (noRenderModule().getHandOpacity()
            != 1.0F
         && RenderUtils.getRenderColor()[3]
            != 255) {
         callback.setReturnValue(Sheets.translucentItemSheet());
      }
   }

   @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
   private static void applyXrayTranslucentChunkLayer(
      BlockState blockState, CallbackInfoReturnable<ChunkSectionLayer> callback
   ) {
      if (xrayModule().isToggled()
         && !xrayModule().alphaSetting
            .isAtMinimum()
         && !xrayModule().alphaSetting
            .isAtMaximum()
         && !xrayModule().shouldRenderBlock(
            blockState
         )) {
         callback.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
      }
   }
}
