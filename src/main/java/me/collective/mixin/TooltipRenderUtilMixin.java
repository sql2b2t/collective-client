package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.TooltipsModule;
import java.awt.Color;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
         target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIII)V",
         ordinal = 1
      )
   )
   private static boolean renderColoredTooltipSprite(
      GuiGraphics graphics, Function<ResourceLocation, RenderType> renderTypeGetter, ResourceLocation sprite, int x, int y, int width, int height
   ) {
      Color tooltipColor = tooltipsModule().getTooltipColor();
      if (tooltipsModule().isToggled() && tooltipColor != null) {
         graphics.blitSprite(
            renderTypeGetter,
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
