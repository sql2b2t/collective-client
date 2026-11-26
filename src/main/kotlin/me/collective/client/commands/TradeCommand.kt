package me.collective.client.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientContext
import me.collective.client.gui.screens.TradeEditorScreen
import me.collective.client.gui.screens.TradeManagerScreen
import me.collective.client.managers.CommandRegistry
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.TradeDefinition
import me.collective.client.utils.inventory.TradeEnchantmentEntry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent.RunCommand
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.world.item.Item

class TradeCommand : Command("trade") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val fromArgument = argument("from", RegistryEntryArgument.registry(BuiltInRegistries.ITEM)) as RequiredArgumentBuilder<Any?, *>
        val toArgument = argument("to", RegistryEntryArgument.registry(BuiltInRegistries.ITEM)) as RequiredArgumentBuilder<Any?, *>
        val priceArgument = (argument("max_price", IntegerArgumentType.integer(1, 64)) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            createTrade(context)
        }

        val createTradeBranch = toArgument.then(priceArgument).executes { context ->
            createTrade(context)
        }
        val itemsBranch = fromArgument.then(createTradeBranch)
        val newEditorBranch = (argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>)
            .then(itemsBranch)
            .executes { context ->
                openNewTradeEditor(context)
            }
        val newBranch = literal<Any?>("new").then(newEditorBranch)
        root.then(newBranch)

        val editArgument = (argument("trade", RegistryArgumentType.create(ClientContext.tradeRegistry)) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            editTrade(context)
        }
        root.then(literal<Any?>("edit").then(editArgument))

        val deleteArgument = (argument("trade", RegistryArgumentType.create(ClientContext.tradeRegistry)) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            deleteTrade(context)
        }
        root.then(literal<Any?>("delete").then(deleteArgument))

        val clearBranch = literal<Any?>("clear").executes { context ->
            clearEnchantments(context)
        }
        val enchantTradeArgument = argument("trade", RegistryArgumentType.create(ClientContext.tradeRegistry)) as RequiredArgumentBuilder<Any?, *>
        enchantTradeArgument.then(clearBranch)

        val enchantmentArgument = argument("enchantment", EnchantmentIdArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val levelArgument = (argument("lvl", IntegerArgumentType.integer()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            addEnchantment(context)
        }
        val addEnchantmentBranch = enchantmentArgument.then(levelArgument)
        enchantTradeArgument.then(addEnchantmentBranch)
        root.then(literal<Any?>("enchant").then(enchantTradeArgument))

        val listBranch = literal<Any?>("list").executes { context ->
            listTrades(context)
        }
        root.then(listBranch)

        root.executes {
            scheduleScreenChange(TradeManagerScreen())
        }
    }

    private fun openNewTradeEditor(context: CommandContext<Any?>): Int {
        val name = context.getArgument("name", String::class.java)
        val editor = TradeEditorScreen(name)
        return scheduleScreenChange(editor)
    }

    private fun deleteTrade(context: CommandContext<Any?>): Int {
        val trade = context.getArgument("trade", TradeDefinition::class.java)
        ClientContext.tradeRegistry.unregisterEntry(trade)

        val message = "Trade %s has been deleted".format(trade.name())
        val component = Component.literal(message)
        val signature = ChatUtils.createSignature(-1)
        ChatUtils.printChat(component, signature)
        return 1
    }

    private fun clearEnchantments(context: CommandContext<Any?>): Int {
        val trade = context.getArgument("trade", TradeDefinition::class.java)
        @Suppress("UNCHECKED_CAST")
        val enchantments = trade.enchants() as MutableList<TradeEnchantmentEntry>
        enchantments.clear()

        val message = "Trade %s enchants has been cleared".format(trade.name())
        val component = Component.literal(message)
        val signature = ChatUtils.createSignature(-1)
        ChatUtils.printChat(component, signature)
        return 1
    }

    private fun createTrade(context: CommandContext<Any?>): Int {
        val name = context.getArgument("name", String::class.java)
        val fromItem = context.getArgument("from", Item::class.java)
        val toItem = context.getArgument("to", Item::class.java)
        val maxPrice = try {
            context.getArgument("max_price", Int::class.javaObjectType)
        } catch (missingPrice: IllegalArgumentException) {
            64
        }

        val createdTrade = TradeDefinition(name, fromItem, toItem, maxPrice, ArrayList())
        ClientContext.tradeRegistry.registerTrade(createdTrade)

        val message = "Trade %s has been created".format(name)
        val component = Component.literal(message)
        val signature = ChatUtils.createSignature(-1)
        ChatUtils.printChat(component, signature)
        return 1
    }

    private fun editTrade(context: CommandContext<Any?>): Int {
        val trade = context.getArgument("trade", TradeDefinition::class.java)
        val editor = TradeEditorScreen(trade)
        return scheduleScreenChange(editor)
    }

    private fun addEnchantment(context: CommandContext<Any?>): Int {
        val trade = context.getArgument("trade", TradeDefinition::class.java)
        val enchantment = context.getArgument("enchantment", String::class.java)
        val level = context.getArgument("lvl", Int::class.javaObjectType)
        @Suppress("UNCHECKED_CAST")
        val enchantments = trade.enchants() as MutableList<TradeEnchantmentEntry>
        val enchantmentEntry = TradeEnchantmentEntry(enchantment, level)
        enchantments.add(enchantmentEntry)

        val message = "Added enchantment %s with level %d to %s trade".format(enchantment, level, trade.name())
        val component = Component.literal(message)
        val signature = ChatUtils.createSignature(-1)
        ChatUtils.printChat(component, signature)
        return 1
    }

    private fun listTrades(context: CommandContext<Any?>): Int {
        val registry = ClientContext.tradeRegistry
        @Suppress("UNCHECKED_CAST")
        val trades = registry.getRegistry() as List<TradeDefinition>
        val entries = trades.map { createTradeListEntry(it) }
        if (entries.isEmpty()) {
            val emptyComponent = Component.literal("You don't have any trades yet.")
            ChatUtils.printChat(emptyComponent, -1)
            return 1
        } else {
            val component = Component.empty().append("Trades list: ")
            val separatorComponent = Component.literal(", ")
            val formattedEntries = ComponentUtils.formatList(entries, separatorComponent)
            component.append(formattedEntries)
            ChatUtils.printChat(component, -1)
            return 1
        }
    }

    companion object {
        private fun createTradeListEntry(trade: TradeDefinition): Component {
            val prefix = CommandRegistry.getPrefix()
            val tradeName = trade.name()
            val editCommand = "${prefix}trade edit $tradeName"
            val component = Component.literal(tradeName)
            val hoverEvent = ShowText(Component.literal("Click to edit"))
            val clickEvent = RunCommand(editCommand)
            component.withStyle { style ->
                style.withHoverEvent(hoverEvent).withClickEvent(clickEvent)
            }
            return component
        }
    }
}
