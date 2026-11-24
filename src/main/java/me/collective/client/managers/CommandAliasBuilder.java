package me.collective.client.managers;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class CommandAliasBuilder extends LiteralArgumentBuilder {
   public CommandAliasBuilder(String literal) {
      super(literal);
   }

   @Override
   public LiteralCommandNode build() {
      CommandAliasBuilder.CommandAliasNode node = new CommandAliasBuilder.CommandAliasNode(
         getLiteral(), getCommand(), getRequirement(), getRedirect(), getRedirectModifier(), isFork()
      );
      Iterator children = this.getArguments().iterator();

      while (children.hasNext()) {
         CommandNode child = (CommandNode)children.next();
         node.addChild(child);
      }

      return node;
   }

   public static class CommandAliasNode extends LiteralCommandNode {

      public CommandAliasNode(String literal, com.mojang.brigadier.Command command, Predicate requirement, CommandNode redirect, RedirectModifier redirectModifier, boolean forks) {
         super(literal, command, requirement, redirect, redirectModifier, forks);
      }

      @Override
      public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
         String literalName = this.getName();
         String remaining = builder.getRemainingLowerCase();
         if (literalName.startsWith(remaining)) {
            String remainingForLength = builder.getRemainingLowerCase();
            int typedLength = remainingForLength.length();

            if (typedLength > 3) {
               String literalToSuggest = this.getName();
               SuggestionsBuilder resultBuilder = builder.suggest(literalToSuggest);
               return resultBuilder.buildFuture();
            }
         }

         return Suggestions.empty();
      }

   }
}
