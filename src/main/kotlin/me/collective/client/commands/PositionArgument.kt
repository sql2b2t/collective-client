package me.collective.client.commands

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider.TextCoordinates
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.commands.arguments.coordinates.WorldCoordinate
import net.minecraft.world.phys.Vec3

class PositionArgument : ArgumentType<Vec3> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Vec3 {
      val start = reader.cursor
      val player = Minecraft.getInstance().player
         ?: throw SimpleCommandExceptionType(LiteralMessage(NOT_IN_WORLD_MESSAGE)).createWithContext(reader)
      if (reader.canRead() && reader.peek() == '^') {
         val left = readLocal(reader)
         readSeparator(reader, start)
         val up = readLocal(reader)
         readSeparator(reader, start)
         val forward = readLocal(reader)
         val direction = player.lookAngle
         val upwards = player.getUpVector(1.0f)
         val leftwards = upwards.cross(direction)
         return player.position().add(leftwards.scale(left)).add(upwards.scale(up)).add(direction.scale(forward))
      }
      val x = WorldCoordinate.parseDouble(reader, false)
      readSeparator(reader, start)
      val y = WorldCoordinate.parseDouble(reader, false)
      readSeparator(reader, start)
      val z = WorldCoordinate.parseDouble(reader, false)
      val position = player.position()
      return Vec3(x.get(position.x), y.get(position.y), z.get(position.z))
   }

   override fun getExamples(): Collection<String> {
      return EXAMPLES
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val source = context.source
      if (source !is SharedSuggestionProvider) {
         return Suggestions.empty()
      }

      val remaining = builder.remaining
      if (remaining.isNotEmpty()) {
         val prefix = remaining[0]
         if (prefix == '^') {
            val localCoordinates = TextCoordinates.DEFAULT_LOCAL
            val localSuggestions = setOf(localCoordinates)
            val validator = Commands.createValidator(this::parse)
            return SharedSuggestionProvider.suggestCoordinates(remaining, localSuggestions, builder, validator)
         }
      }

      val absoluteSuggestions = source.absoluteCoordinates
      val validator = Commands.createValidator(this::parse)
      return SharedSuggestionProvider.suggestCoordinates(remaining, absoluteSuggestions, builder, validator)
   }

   companion object {
      private val EXAMPLES: Collection<String> = listOf("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "0.1 -0.5 .9", "~0.5 ~1 ~-5")
      private const val NOT_IN_WORLD_MESSAGE = "Not in a world"

      @JvmStatic
      @Throws(CommandSyntaxException::class)
      private fun readSeparator(reader: StringReader, start: Int) {
         if (!reader.canRead() || reader.peek() != ' ') {
            reader.cursor = start
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader)
         }
         reader.skip()
      }

      @JvmStatic
      @Throws(CommandSyntaxException::class)
      private fun readLocal(reader: StringReader): Double {
         if (!reader.canRead() || reader.peek() != '^') {
            throw SimpleCommandExceptionType(LiteralMessage("Cannot mix local and world coordinates.")).createWithContext(reader)
         }
         reader.skip()
         return if (!reader.canRead() || reader.peek() == ' ') 0.0 else reader.readDouble()
      }
   }
}
