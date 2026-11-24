package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientContext;
import me.collective.client.access.TooltipComponentCapture;
import me.collective.client.render.FontActiveTextCollector;
import me.collective.client.utils.inventory.ShulkerBoxItemPreview;
import me.collective.client.gui.screens.ShulkerPreviewScreen;
import me.collective.client.modules.render.TooltipsModule;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.ActiveTextCollector.Parameters;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.HoveredTextEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
   @Unique
   private boolean renderingPreviewItemCount;
   private static TooltipsModule tooltips;
   private static TooltipsModule tooltips() {
      if (tooltips == null) {
         tooltips = ClientContext.getModule(TooltipsModule.class);
      }
      return tooltips;
   }

   @Shadow
   public abstract void renderItemCount(Font font, ItemStack stack, int x, int y, @Nullable String countText);

   @Shadow
   protected abstract Parameters createDefaultTextParameters(float opacity);

   @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemBar(Lnet/minecraft/world/item/ItemStack;II)V"), method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V")
   private void renderContainerItemPreview(
      Font font, ItemStack stack, int x, int y, @Nullable String countText, CallbackInfo callback
   ) {
      if (!tooltips().isToggled() || !tooltips().majorityItemSetting.getValue()) {
         return;
      }
      for (ShulkerBoxItemPreview preview : tooltips().inventoryContainerSnapshots) {
         if (preview.containerStack() != stack || preview.previewStack() == null) {
            continue;
         }
         tooltips().renderItemPreview((GuiGraphics)(Object)this, preview.previewStack(), x, y);
         break;
      }
   }

   @Inject(method = "renderItemCount", at = @At("HEAD"), cancellable = true)
   private void renderEmptyPreviewItemCount(
      Font font, ItemStack stack, int x, int y, String countText, CallbackInfo callback
   ) {
      if (this.renderingPreviewItemCount || !ShulkerPreviewScreen.isRenderingPreview() || stack.getCount() != 0) {
         return;
      }
      this.renderingPreviewItemCount = true;
      this.renderItemCount(font, stack, x, y, ChatFormatting.RED + "0");
      callback.cancel();
      this.renderingPreviewItemCount = false;
   }

   @Inject(
      method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
         shift = Shift.BEFORE
      ),
      cancellable = true
   )
   private void captureTooltipComponents(
      Font font, List text, Optional image, int tooltipX, int tooltipY, Identifier background, CallbackInfo callback, @Local(ordinal = 1) List components
   ) {
      if (TooltipComponentCapture.isCaptureEnabled()) {
         TooltipComponentCapture.setCapturedComponents(components);
         callback.cancel();
      }
   }

   @Inject(method = "textRenderer(Lnet/minecraft/client/gui/GuiGraphics$HoveredTextEffects;Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/ActiveTextCollector;", at = @At("HEAD"), cancellable = true)
   private void overrideChatTextCollector(
      HoveredTextEffects hoverEffects, Consumer submission, CallbackInfoReturnable<ActiveTextCollector> callback
   ) {
      if (FontActiveTextCollector.customChatTextEnabled) {
         FontActiveTextCollector collector = new FontActiveTextCollector(this.createDefaultTextParameters(1.0F), (GuiGraphics)(Object)this, hoverEffects, submission);
         callback.setReturnValue(collector);
      }
   }
}
