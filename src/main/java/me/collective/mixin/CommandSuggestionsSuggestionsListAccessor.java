package me.collective.mixin;

import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SuggestionsList.class)
public interface CommandSuggestionsSuggestionsListAccessor {
   @Accessor("current")
   int getSelection();

   @Accessor("originalContents")
   String getTypedText();
}
