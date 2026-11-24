package me.collective.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
   @Invoker("renderTooltip")
   void drawTooltipsHook(Font font, List components, int mouseX, int mouseY, ClientTooltipPositioner positioner, @Nullable Identifier background);

   @Accessor("hoveredTextStyle")
   void collective$hoverStyle(Style hoveredTextStyle);

   @Accessor("clickableTextStyle")
   void collective$clickStyle(Style clickableTextStyle);

   @Accessor("mouseX")
   int collective$mouseX();

   @Accessor("mouseY")
   int collective$mouseY();

   @Invoker("submitBlit")
   void collective$submitBlit(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color);
}
