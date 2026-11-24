package me.collective.client.managers;

import me.collective.client.ClientContext;
import me.collective.client.commands.Command;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ModuleAliasBuilder extends LiteralArgumentBuilder {

   public ModuleAliasBuilder(String literal) {
      super(literal);
   }

   @Override
   public LiteralCommandNode build() {
      ModuleAliasBuilder.ModuleAliasNode node = new ModuleAliasBuilder.ModuleAliasNode(
         getLiteral(), getCommand(), getRequirement(), getRedirect(), getRedirectModifier(), isFork()
      );
      Iterator children = this.getArguments().iterator();

      while (children.hasNext()) {
         CommandNode child = (CommandNode)children.next();
         node.addChild(child);
      }

      return node;
   }

   public static class ModuleAliasNode extends LiteralCommandNode {

      public ModuleAliasNode(String literal, com.mojang.brigadier.Command command, Predicate requirement, CommandNode redirect, RedirectModifier redirectModifier, boolean forks) {
         super(literal, command, requirement, redirect, redirectModifier, forks);
      }

      @Override
      public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
         CommandRegistry registry = ClientContext.commandRegistry;
         Iterator commands = ((List)registry.getRegistry()).iterator();

         while (commands.hasNext()) {
            Command command = (Command)commands.next();
            String commandName = command.getName();
            String remaining = builder.getRemainingLowerCase();
            if (commandName.startsWith(remaining)) {
               return Suggestions.empty();
            }
         }

         return super.listSuggestions(context, builder);
      }

   }
}
