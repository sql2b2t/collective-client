package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.modules.misc.BetterChatModule;
import me.collective.client.ClientContext;
import me.collective.client.access.ChatLineAccess;
import me.collective.client.access.ChatGraphicsXOffset;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/client/gui/components/ChatComponent$1")
public class ChatComponent1Mixin {
   private static NoRenderModule noRenderModule;
   private static BetterChatModule betterChatModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static BetterChatModule betterChatModule() {
      if (betterChatModule == null) {
         betterChatModule = ClientContext.getModule(BetterChatModule.class);
      }
      return betterChatModule;
   }

   @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GuiMessage$Line;tag()Lnet/minecraft/client/GuiMessageTag;"))
   private GuiMessageTag hideMessageIndicator(GuiMessageTag originalTag) {
      return noRenderModule().isToggled()
            && noRenderModule().messageIndicatorSetting.getValue()
         ? null
         : originalTag;
   }

   @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
   private boolean suppressMessageIndicatorIcon(
      ChatGraphicsAccess graphics, int messageY, float opacity, FormattedCharSequence text, Operation original, @Local(argsOnly = true) Line line
   ) {
      ChatLineAccess lineAccess = (ChatLineAccess)(Object)line;
      Minecraft minecraft = Minecraft.getInstance();
      int offsetX = 0;
      offsetX -= noRenderModule().isToggled()
            && noRenderModule().messageIndicatorSetting.getValue()
         ? 3
         : 0;
      if (betterChatModule().isAnimationEnabled()) {
         lineAccess.getProgress()
            .setTarget(
               true
            );
         float progress = lineAccess.getProgress().updateAndGet();
         long addedAtMillis = lineAccess.collective$getAddTime();
         boolean animateBothAxes = betterChatModule().typeSetting.getValue()
            == BetterChatModule.TypeChoice.BOTH;
         if (betterChatModule().typeSetting.getValue()
               == BetterChatModule.TypeChoice.VERTICAL
            || animateBothAxes) {
            messageY = (int)(messageY + 9.0F * (1.0F - progress));
         }

         if (betterChatModule().typeSetting.getValue()
               == BetterChatModule.TypeChoice.HORIZONTAL
            || animateBothAxes) {
            offsetX = (int)(offsetX - minecraft.font.width(text) * (1.0F - progress));
         }

         if (betterChatModule().alphaSetting.getValue() != 0) {
            float fadeProgress = Mth.clamp(
               (float)(System.currentTimeMillis() - addedAtMillis)
                  / (betterChatModule().alphaSetting.getValue())
                     .intValue(),
               0.0F,
               1.0F
            );
            opacity *= fadeProgress;
            if (opacity < 0.05) {
               opacity = 0.0F;
            }
         }
      }

      if (graphics instanceof ChatGraphicsXOffset offsetGraphics) {
         offsetGraphics.collective$updateX(offsetX);
      }

      return (Boolean)original.call(graphics, messageY, opacity, text);
   }
}
