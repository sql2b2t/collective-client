package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientContext
import me.collective.client.render.ColorUtils
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.block.Block
import java.awt.Color

class ColorCommand : Command("color") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val blocksCommand = literal<Any?>("blocks")

        val blockRegistry = BuiltInRegistries.BLOCK
        val blockArgumentType = RegistryEntryArgument.registry(blockRegistry)
        var blockArgument = argument("block", blockArgumentType) as RequiredArgumentBuilder<Any?, *>

        val setCommand = literal<Any?>("set")
        val hexArgument = argument("hex", HexColorArgument()) as RequiredArgumentBuilder<Any?, *>
        val setColorArgument = hexArgument.executes { context -> setBlockColor(context) }
        val setBranch = setCommand.then(setColorArgument)
        blockArgument = blockArgument.then(setBranch) as RequiredArgumentBuilder<Any?, *>

        val clearCommand = literal<Any?>("clear")
        val clearBranch = clearCommand.executes { context -> clearBlockColor(context) }
        val blockBranches = blockArgument.then(clearBranch)
        val listCommand = blocksCommand.then(blockBranches) as LiteralArgumentBuilder<Any?>
        val blocksBranch = listCommand.executes { context -> listBlockColors(context) }
        root.then(blocksBranch)
    }

    private fun setBlockColor(context: CommandContext<*>): Int {
        val block = context.getArgument("block", Block::class.java) as Block
        val color = context.getArgument("hex", Color::class.java) as Color
        val registry = ClientContext.blockColorRegistry
        registry.putColor(block, color)

        val message = Component.empty()
        val messagePrefix = "Set %s color to ".format(getBlockName(block))
        message.append(messagePrefix)
        val hexColor = ColorUtils.toHex(color, true)
        val colorComponent = Component.literal(hexColor)
        val coloredHex = colorComponent.withColor(color.hashCode())
        message.append(coloredHex)
        message.append(".")
        ChatUtils.printChat(message, -1)
        return 1
    }

    private fun clearBlockColor(context: CommandContext<*>): Int {
        val block = context.getArgument("block", Block::class.java) as Block
        val registry = ClientContext.blockColorRegistry
        registry.putColor(block, null)

        val messageText = "Clear %s color.".format(getBlockName(block))
        val message = Component.literal(messageText)
        ChatUtils.printChat(message, -1)
        return 1
    }

    private fun listBlockColors(context: CommandContext<*>): Int {
        val message = Component.empty()
        val registry = ClientContext.blockColorRegistry

        val count = registry.colors.size
        val pluralSuffix = if (count == 1) "" else "s"
        val listDelimiter = if (count == 0) "" else ":"

        val messagePrefix = "%d block color%s%s".format(count, pluralSuffix, listDelimiter)
        message.append(messagePrefix)

        val colors = registry.colors
        colors.forEach { (block, color) ->
            val rgbColor = ColorUtils.formatRgb(color, false)
            message.append("\n")

            val blockName = getBlockName(block)
            val styledBlockName = Component.literal(blockName).withStyle(ChatFormatting.GRAY)
            message.append(styledBlockName)

            message.append(": ")
            val coloredRgb = Component.literal(rgbColor).withColor(color.hashCode())
            message.append(coloredRgb)
        }
        ChatUtils.printChat(message, -1)
        return 1
    }

    companion object {
        private fun getBlockName(block: Block): String {
            val registry = BuiltInRegistries.BLOCK
            val identifier = registry.getKey(block)
            return identifier.toShortLanguageKey()
        }
    }
}
