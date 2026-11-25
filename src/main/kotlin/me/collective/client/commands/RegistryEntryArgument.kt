package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.collections.NamedRegistryEntry
import me.collective.client.utils.collections.RegistryGroupMatcher
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.Holder.Reference
import net.minecraft.core.Registry
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CompletableFuture
import java.util.function.BiPredicate
import java.util.function.IntFunction
import java.util.stream.Stream

open class RegistryEntryArgument<T : Any>(
   private val registry: Registry<T>,
   private val suggestionFilter: BiPredicate<in CommandContext<*>, in T>
) : ArgumentType<T>, ClientAccess {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): T {
      val input = reader.readString()
      val id = try {
         ResourceLocation.parse(input)
      } catch (invalidIdentifier: Exception) {
         val invalidIdType = DynamicCommandExceptionType { missingValue ->
            Component.nullToEmpty(MISSING_ENTRY_PREFIX + missingValue)
         }
         throw invalidIdType.create(input)
      }
      val entry = this.registry.get(id)
      if (entry.isEmpty) {
         val exceptionType = DynamicCommandExceptionType { missingValue ->
            Component.nullToEmpty(MISSING_ENTRY_PREFIX + missingValue)
         }
         throw exceptionType.create(input)
      }
      @Suppress("UNCHECKED_CAST")
      val holder = entry.get() as Reference<T>
      return holder.value()
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val ids = this.registry.keySet()
      val suggestions = ids.stream()
         .filter { id ->
            val value = this.registry.getValue(id)
            value != null && this.suggestionFilter.test(context, value)
         }
         .map { it.toShortLanguageKey() }
      return SharedSuggestionProvider.suggest(suggestions, builder)
   }

   companion object {
      private const val MISSING_ENTRY_PREFIX = "Element not found "

      @JvmStatic
      fun <T : Any> registry(registry: Registry<T>, suggestionFilter: BiPredicate<in CommandContext<*>, in T>): ArgumentType<T> {
         return RegistryEntryArgument(registry, suggestionFilter)
      }

      @JvmStatic
      fun <T : Any> registry(registry: Registry<T>): ArgumentType<T> {
         return RegistryEntryArgument(registry) { _, _ -> true }
      }

      @JvmStatic
      fun <T : Any> aliased(registry: Registry<T>, arrayFactory: IntFunction<Array<T>>): ArgumentType<Array<T>> {
         return GroupedEntries(registry, { _, _ -> true }, arrayFactory)
      }

      @JvmStatic
      fun <T : Any> aliased(
         registry: Registry<T>,
         suggestionFilter: BiPredicate<in CommandContext<*>, in T>,
         arrayFactory: IntFunction<Array<T>>
      ): ArgumentType<Array<T>> {
         return GroupedEntries(registry, suggestionFilter, arrayFactory)
      }
   }

   class GroupedEntries<T : Any>(
      private val registry: Registry<T>,
      private val suggestionFilter: BiPredicate<in CommandContext<*>, in T>,
      private val arrayFactory: IntFunction<Array<T>>
   ) : ArgumentType<Array<T>>, ClientAccess {
      private val groupMatcher: RegistryGroupMatcher = RegistryGroupMatcher.forRegistry(registry)

      @Throws(CommandSyntaxException::class)
      override fun parse(reader: StringReader): Array<T> {
         val input = reader.readString()
         val groupEntries = this.groupMatcher.getGroupEntries(input)
         if (!groupEntries.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return groupEntries.stream().map { it.value() as T }.toArray(this.arrayFactory)
         }

         val id = try {
            ResourceLocation.parse(input)
         } catch (invalidIdentifier: Exception) {
            val invalidIdType = DynamicCommandExceptionType { missingValue ->
               Component.nullToEmpty(MISSING_ENTRY_PREFIX + missingValue)
            }
            throw invalidIdType.create(input)
         }
         val entry = this.registry.get(id)
         if (entry.isEmpty) {
            val exceptionType = DynamicCommandExceptionType { missingValue ->
               Component.nullToEmpty(MISSING_ENTRY_PREFIX + missingValue)
            }
            throw exceptionType.create(input)
         }
         @Suppress("UNCHECKED_CAST")
         val holder = entry.get() as Reference<T>
         val value = holder.value()
         val result = this.arrayFactory.apply(1)
         result[0] = value
         return result
      }

      override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
         val groupNames = this.groupMatcher.getGroupNames()
         val groupSuggestions = groupNames.stream().filter { candidateName: String ->
            val groupEntries = this.groupMatcher.getGroupEntries(candidateName)
            groupEntries.stream().map { it.value() as T }
               .anyMatch { candidateValue -> this.suggestionFilter.test(context, candidateValue) }
         }
         val ids = this.registry.keySet()
         val entrySuggestions = ids.stream()
            .filter { id ->
               val value = this.registry.getValue(id)
               value != null && this.suggestionFilter.test(context, value)
            }
            .map { it.toShortLanguageKey() }
         val suggestions = Stream.concat(groupSuggestions, entrySuggestions)
         return SharedSuggestionProvider.suggest(suggestions, builder)
      }

      companion object {
         private const val MISSING_ENTRY_PREFIX = "Element not found: "
      }
   }
}
