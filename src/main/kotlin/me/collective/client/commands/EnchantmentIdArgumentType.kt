package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.concurrent.CompletableFuture

class EnchantmentIdArgumentType : ArgumentType<String>, ClientAccess {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      val enchantmentId = reader.readString()
      val level = ClientAccess.collectiveMinecraft.level
         ?: throw SimpleCommandExceptionType(Component.nullToEmpty(NOT_IN_WORLD_MESSAGE)).createWithContext(reader)
      val registryAccess = level.registryAccess()
      val enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
      val identifier = try {
         Identifier.parse(enchantmentId)
      } catch (invalidIdentifier: Exception) {
         throw SimpleCommandExceptionType(Component.nullToEmpty(UNKNOWN_ENCHANTMENT_PREFIX + enchantmentId)).createWithContext(reader)
      }
      val enchantment = enchantments.get(identifier)

      if (enchantment.isEmpty) {
         val unknownEnchantmentType = DynamicCommandExceptionType { argument ->
            val errorMessage = UNKNOWN_ENCHANTMENT_PREFIX + argument
            Component.nullToEmpty(errorMessage)
         }
         throw unknownEnchantmentType.create(enchantmentId)
      }
      return enchantmentId
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val level = ClientAccess.collectiveMinecraft.level ?: return Suggestions.empty()
      val registryAccess = level.registryAccess()
      val enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
      val keys = enchantments.keySet()
      val names = keys.stream().map { it.toShortLanguageKey() }
      return SharedSuggestionProvider.suggest(names, builder)
   }

   companion object {
      private const val UNKNOWN_ENCHANTMENT_PREFIX = "Element not found "
      private const val NOT_IN_WORLD_MESSAGE = "Not in a world"
   }
}
