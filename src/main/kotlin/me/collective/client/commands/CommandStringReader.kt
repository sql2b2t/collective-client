package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException

object CommandStringReader {

   @JvmStatic
   @Throws(CommandSyntaxException::class)
   fun readString(reader: StringReader): String {
      if (!reader.canRead()) {
         return ""
      }

      val firstCharacter = reader.peek()
      if (StringReader.isQuotedStringStart(firstCharacter)) {
         reader.skip()
         return reader.readStringUntil(firstCharacter)
      }

      val startCursor = reader.cursor

      while (reader.canRead() && !isStringTerminator(reader.peek())) {
         reader.read()
      }

      val sourceSnapshot = reader.string
      val endCursor = reader.cursor
      return sourceSnapshot.substring(startCursor, endCursor)
   }

   @JvmStatic
   private fun isStringTerminator(character: Char): Boolean {
      return character == ' ' || character == '\t'
   }
}
