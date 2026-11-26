package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.collective.client.ClientContext;
import me.collective.client.render.ClientRenderPipelines;
import me.collective.client.modules.render.TooltipsModule;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TooltipRenderUtil.class)
public class TooltipRenderUtilMixin {
   private static TooltipsModule tooltipsModule;
   private static TooltipsModule tooltipsModule() {
      if (tooltipsModule == null) {
         tooltipsModule = ClientContext.getModule(TooltipsModule.class);
      }
      return tooltipsModule;
   }

   @WrapWithCondition(
      method = "renderTooltipBackground",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
         ordinal = 1
      )
   )
   private static boolean renderColoredTooltipSprite(
      GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height
   ) {
      Color tooltipColor = tooltipsModule().getTooltipColor();
      if (tooltipsModule().isToggled() && tooltipColor != null) {
         graphics.blitSprite(
            ClientRenderPipelines.POSITION_TEX_COLOR,
            sprite,
            x,
            y,
            width,
            height,
            tooltipColor.hashCode()
         );
         return false;
      } else {
         return true;
      }
   }
}
