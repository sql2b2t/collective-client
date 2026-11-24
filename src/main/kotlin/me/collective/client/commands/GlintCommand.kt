package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientContext
import me.collective.client.managers.GlintRegistry
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.SetSetting
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item

class GlintCommand : Command("glint") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val commandBuilder = builder as LiteralArgumentBuilder<Any?>

        val glintType = EnumArgumentType(GlintRegistry.GlintMode::class.java, "Glint")
        var glintArgument = argument("glint", glintType) as RequiredArgumentBuilder<Any?, *>
        val clearBuilder = literal<Any?>("clear")
        val clearExecutor = { context: CommandContext<Any?> -> clearGlintItems(context) }
        val clearBranch = clearBuilder.executes(clearExecutor)
        glintArgument = glintArgument.then(clearBranch) as RequiredArgumentBuilder<Any?, *>

        val itemRegistry = BuiltInRegistries.ITEM
        val itemType = RegistryEntryArgument.registry(itemRegistry)
        val itemBuilder = argument("item", itemType) as RequiredArgumentBuilder<Any?, *>
        val itemExecutor = { context: CommandContext<Any?> -> setItemGlint(context) }
        val itemBranch = itemBuilder.executes(itemExecutor)
        val completedGlintArgument = glintArgument.then(itemBranch)
        commandBuilder.then(completedGlintArgument)
    }

    private fun setItemGlint(commandContext: CommandContext<*>): Int {
        val glintMode = commandContext.getArgument("glint", GlintRegistry.GlintMode::class.java) as GlintRegistry.GlintMode
        val selectedItem = commandContext.getArgument("item", Item::class.java) as Item
        val itemName = selectedItem.defaultInstance.hoverName.string
        val glintManager = ClientContext.glintRegistry
        val added = glintManager.toggle(selectedItem, glintMode)
        ChatUtils.printChat(
            Component.literal(if (added) "Added " else "Removed ")
                .append(formatText(itemName))
                .append(if (added) " to glint list \"" else " from glint list \"")
                .append(formatText(glintMode.name))
                .append("\""),
            ChatUtils.DEFAULT_SIGNATURE
        )
        return 1
    }

    private fun clearGlintItems(commandContext: CommandContext<*>): Int {
        val capturedGlintMode = commandContext.getArgument("glint", GlintRegistry.GlintMode::class.java) as GlintRegistry.GlintMode
        val glintManager = ClientContext.glintRegistry
        val configuredItems = glintManager.getItems(capturedGlintMode)
        (configuredItems as? SetSetting<Item>)?.clear() ?: (configuredItems.value as? MutableSet<Item>)?.clear()
        ChatUtils.printChat(
            Component.literal("Glint list \"")
                .append(formatText(capturedGlintMode.name))
                .append("\" has been cleared"),
            ChatUtils.DEFAULT_SIGNATURE
        )
        return 1
    }
}
