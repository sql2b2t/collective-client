package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.modules.misc.BetterChatModule;
import me.collective.client.ClientContext;
import me.collective.client.render.FontRenderer;
import me.collective.client.render.AtlasTextRenderer;
import me.collective.client.modules.client.FontsModule;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ComponentRenderUtils.class)
public class ComponentRenderUtilsMixin {
   private static BetterChatModule betterChatModule;
   private static FontsModule fontsModule;
   private static BetterChatModule betterChatModule() {
      if (betterChatModule == null) {
         betterChatModule = ClientContext.getModule(BetterChatModule.class);
      }
      return betterChatModule;
   }
   private static FontsModule fontsModule() {
      if (fontsModule == null) {
         fontsModule = ClientContext.getModule(FontsModule.class);
      }
      return fontsModule;
   }

   @ModifyExpressionValue(method = "wrapComponents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;getSplitter()Lnet/minecraft/client/StringSplitter;"))
   private static StringSplitter useChatFontSplitter(StringSplitter originalSplitter) {
      AtlasTextRenderer textRenderer = FontRenderer.INSTANCE
         .getRenderer();
      return textRenderer != null
            && fontsModule().isToggled()
            && fontsModule().chatSetting.getValue()
         ? textRenderer.getSplitter()
         : originalSplitter;
   }

   @ModifyVariable(method = "wrapComponents", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static FormattedText highlightOwnName(FormattedText originalText) {
      return betterChatModule().highlightNames(originalText);
   }

}
