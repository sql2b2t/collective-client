package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import me.collective.client.ClientAccess;
import me.collective.client.managers.CommandRegistry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin implements ClientAccess {
   @Shadow
   @Final
   EditBox input;
   @Shadow
   boolean keepSuggestions;
   @Shadow
   private ParseResults currentParse;
   @Shadow
   private CompletableFuture pendingSuggestions;
   @Shadow
   private SuggestionsList suggestions;

   @Shadow
   public abstract void showSuggestions(boolean narrate);

   @Inject(method = "updateCommandInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true)
   public void updateClientCommandSuggestions(CallbackInfo callback, @Local StringReader reader) {
      String prefix = CommandRegistry.getPrefix();
      if (reader.canRead(prefix.length()) && reader.getString().startsWith(prefix, reader.getCursor())) {
         reader.setCursor(reader.getCursor() + prefix.length());
         CommandDispatcher dispatcher = CommandRegistry.commandDispatcher;
         if (this.currentParse == null) {
            this.currentParse = dispatcher.parse(
               reader, CommandRegistry.suggestionSource
            );
         }

         int cursor = this.input.getCursorPosition();
         if (cursor >= 1 && (this.suggestions == null || !this.keepSuggestions)) {
            this.pendingSuggestions = dispatcher.getCompletionSuggestions(this.currentParse, cursor);
            this.pendingSuggestions.thenRun(() -> {
               if (this.pendingSuggestions.isDone()) {
                  this.showSuggestions(false);
               }
            });
         }

         callback.cancel();
      }
   }
}
