package me.collective.mixin;

import me.collective.client.utils.math.MathConstants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import me.collective.client.modules.misc.BetterChatModule;
import me.collective.client.ClientContext;
import me.collective.client.access.ChatLineAccess;
import me.collective.client.gui.screens.PresetImportScreen;
import me.collective.client.render.FontActiveTextCollector;
import me.collective.client.managers.CommandRegistry;
import me.collective.client.modules.client.FontsModule;
import me.collective.client.modules.client.HUDModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
   private static FontsModule fontsModule;
   private static BetterChatModule betterChatModule;
   private static HUDModule hudModule;
   private static FontsModule fontsModule() {
      if (fontsModule == null) {
         fontsModule = ClientContext.getModule(FontsModule.class);
      }
      return fontsModule;
   }
   private static BetterChatModule betterChatModule() {
      if (betterChatModule == null) {
         betterChatModule = ClientContext.getModule(BetterChatModule.class);
      }
      return betterChatModule;
   }
   private static HUDModule hudModule() {
      if (hudModule == null) {
         hudModule = ClientContext.getModule(HUDModule.class);
      }
      return hudModule;
   }
   @Shadow
   protected EditBox input;
   @Shadow
   CommandSuggestions commandSuggestions;

   @Shadow
   protected abstract boolean insertionClickMode();

   protected ChatScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init()V", at = @At("HEAD"))
   private void resetChatLineAnimations(CallbackInfo callback) {
      if (betterChatModule().alwaysSetting.getValue()
         && betterChatModule().isAnimationEnabled()) {
         for (Line line : Minecraft.getInstance().gui.hud.getChat().trimmedMessages) {
            ((ChatLineAccess)(Object)line)
               .getProgress()
               .setValue(
                  false
               );
            ((ChatLineAccess)(Object)line).collective$setAddTime(System.currentTimeMillis());
         }
      }
   }

   @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
   private boolean allowChatInputBackgroundFill(
      GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color
   ) {
      return false;
   }

   @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
   private boolean allowChatSuperclassRender(
      Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
   ) {
      return hudModule().getChatInputOffset()
            / 12.0F
         > MathConstants.ONE_QUARTER;
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", shift = Shift.BEFORE)
   )
   private void updateInlineCommandSuggestion(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback
   ) {
      String commandInput = this.input.getValue();
      if (!commandInput.startsWith(CommandRegistry.getPrefix())) {
         return;
      }
      CommandSuggestionsSuggestionsListAccessor suggestionsWindow = (CommandSuggestionsSuggestionsListAccessor)((CommandSuggestionsAccessor)this.commandSuggestions).getWindow();
      CompletableFuture<Suggestions> pendingSuggestions = ((CommandSuggestionsAccessor)this.commandSuggestions).getSuggestion();
      if (pendingSuggestions == null || !pendingSuggestions.isDone()
         || !this.input.getValue().startsWith(CommandRegistry.getPrefix())) {
         return;
      }
      String suggestionText = "";
      Suggestions suggestions = pendingSuggestions.join();
      ParseResults<?> parseResults = ((CommandSuggestionsAccessor)this.commandSuggestions).getParse();
      List<String> placeholders = Collections.emptyList();
      if (parseResults != null && this.input.getCursorPosition() == this.input.getValue().length()) {
         List<? extends ParsedCommandNode<?>> parsedNodes = parseResults.getContext().getNodes();
         if (!parseResults.getContext().getNodes().isEmpty()) {
            placeholders = this.buildCommandNodePlaceholders(
               parsedNodes.getLast().getNode().getChildren()
            );
         }
      }

      if (suggestionsWindow != null && !suggestions.getList().isEmpty()) {
         String completedInput = suggestions.getList().get(suggestionsWindow.getSelection()).apply(suggestionsWindow.getTypedText());
         suggestionText = suggestionText + (completedInput.startsWith(this.input.getValue()) ? completedInput.substring(this.input.getValue().length()) : "");
      }

      if (this.input.getCursorPosition() > 0
            && this.input.getValue().charAt(this.input.getCursorPosition() - 1)
               != 32
         || !suggestionText.isEmpty()) {
         suggestionText = suggestionText + " ";
      }

      if (parseResults != null
         && parseResults.getReader().canRead()
         && (
            parseResults.getReader().peek()
                  != 32
               || suggestionsWindow != null
         )
         && !placeholders.isEmpty()) {
         placeholders.removeFirst();
      }

      suggestionText = suggestionText + String.join(" ", placeholders);
      this.input.setSuggestion(suggestionText);
   }

   @ModifyExpressionValue(
      method = "mouseClicked",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;result()Lnet/minecraft/network/chat/Style;")
   )
   private Style findCustomFontClickedStyle(
      Style originalStyle, @Local(argsOnly = true) MouseButtonEvent mouseEvent
   ) {
      if (fontsModule().isToggled()
         && fontsModule().chatSetting.getValue()) {
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         FontActiveTextCollector.ClickableStyleFinder styleFinder = new FontActiveTextCollector.ClickableStyleFinder(mouseEvent.x(), mouseEvent.y(), this.insertionClickMode());
         this.minecraft.gui.hud.getChat().captureClickableText(styleFinder, screenHeight, this.minecraft.gui.hud.getGuiTicks(), net.minecraft.client.gui.components.ChatComponent.DisplayMode.FOREGROUND);
         return styleFinder.result();
      } else {
         return originalStyle;
      }
   }

   @Override
   public void onFilesDrop(List paths) {
      PresetImportScreen.openForDroppedFiles(this, paths);
   }

   @Unique
   private List<String> buildCommandNodePlaceholders(Collection<? extends CommandNode<?>> nodeChildren) {
      List<String> placeholders = new ArrayList<>();

      while (true) {
         CommandNode<?> lastNode = null;
         int childCount = 0;
         StringBuilder groupText = new StringBuilder("<");

         for (CommandNode<?> childNode : nodeChildren) {
            childCount++;
            groupText.append(childNode.getName());
            if (childCount != nodeChildren.size()) {
               groupText.append(", ");
            } else {
               lastNode = childNode;
            }
         }

         groupText.append(">");
         if (!nodeChildren.isEmpty()) {
            placeholders.add(groupText.toString());
         }

         if (nodeChildren.size() != 1 || lastNode == null) {
            return placeholders;
         }

         nodeChildren = lastNode.getChildren();
      }
   }
}
