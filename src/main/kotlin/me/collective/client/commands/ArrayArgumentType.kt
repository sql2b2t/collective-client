package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.lang.reflect.Array as JavaArray
import java.util.concurrent.CompletableFuture

class ArrayArgumentType(
   private val elementArgument: ArgumentType<*>,
   private val elementType: Class<*>
) : ArgumentType<Array<Any?>> {

   constructor(elementArgument: ReifiedArgumentType<*>) : this(elementArgument, elementArgument.getReifiedType())

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Array<Any?> {
      val remainingInput = reader.remaining
      val inputLength = reader.totalLength
      reader.cursor = inputLength
      // Пробельные разделители: одиночные/двойные/концевые пробелы не должны
      // порождать пустые элементы (они ломают разбор элементов).
      val elements = remainingInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toTypedArray()
      val componentType = this.elementType
      @Suppress("UNCHECKED_CAST")
      val parsedElements = JavaArray.newInstance(componentType, elements.size) as Array<Any?>

      for (index in elements.indices) {
         val argument = this.elementArgument
         val elementReader = StringReader(elements[index])
         parsedElements[index] = argument.parse(elementReader)
      }

      return parsedElements
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val elements = builder.remaining.split(" ").toTypedArray()
      if (elements.isEmpty()) {
         val argument = this.elementArgument
         return argument.listSuggestions(context, builder)
      }

      val suggestionStart = builder.start
      val remainingInput = builder.remaining
      val remainingEnd = suggestionStart + remainingInput.length
      val lastElement = elements[elements.size - 1]
      val lastElementStart = remainingEnd - lastElement.length
      val argument = this.elementArgument
      val lastElementSuggestions = SuggestionsBuilder(builder.input, lastElementStart)
      return argument.listSuggestions(context, lastElementSuggestions)
   }
}
