package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException

class FancyStringArgument : ArgumentType<String> {

   fun description(): String {
      return "fancyString()"
   }

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      return CommandStringReader.readString(reader)
   }

   override fun getExamples(): Collection<String> {
      return EXAMPLES
   }

   companion object {
      private val EXAMPLES: List<String> = listOf("words_with_underscores_any_language", "слова_на_любом_языке")

      @JvmStatic
      fun fancyString(): FancyStringArgument {
         return FancyStringArgument()
      }
   }
}
