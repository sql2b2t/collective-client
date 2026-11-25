package me.collective.client.commands

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import me.collective.client.render.ColorUtils
import java.awt.Color

class HexColorArgument : ArgumentType<Color> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Color {
      try {
         if (reader.canRead() && reader.peek() == '#') reader.skip()
         val text = reader.readString()
         return if (text.isEmpty()) Color.WHITE else ColorUtils.parseHexColor(text)
      } catch (failure: IllegalArgumentException) {
         throw SimpleCommandExceptionType(INVALID_COLOR_MESSAGE).createWithContext(reader)
      }
   }

   companion object {
      private val INVALID_COLOR_MESSAGE = LiteralMessage("Invalid color provided.")
   }
}
