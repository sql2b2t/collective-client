package me.collective.mixin;

import com.mojang.brigadier.ParseResults;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
   @Accessor("pendingSuggestions")
   CompletableFuture getSuggestion();

   @Accessor("suggestions")
   SuggestionsList getWindow();

   @Accessor("currentParse")
   ParseResults getParse();
}
