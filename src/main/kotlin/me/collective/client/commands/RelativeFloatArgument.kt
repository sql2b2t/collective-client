package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException

class RelativeFloatArgument : ArgumentType<RelativeFloatArgument.RelativeFloat> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): RelativeFloat {
      val relative = reader.canRead() && reader.peek() == '~'
      if (relative) reader.skip()
      val value = if (relative && (!reader.canRead() || reader.peek() == ' ')) 0.0f else reader.readFloat()
      return RelativeFloat(value, relative)
   }

   @JvmRecord
   data class RelativeFloat(
      val value: Float,
      val relative: Boolean
   )

   companion object {
      @JvmStatic
      fun relativeFloatArg(): RelativeFloatArgument {
         return RelativeFloatArgument()
      }
   }
}
