package me.collective.client.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientAccess
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.ReportedException
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ClickType

class InventoryCommand : Command("inventory") {

    private fun openInventory(context: CommandContext<*>): Int {
        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        val player = ClientAccess.collectiveMinecraft.player ?: return 1
        if (gameMode != null && gameMode.isServerControlledInventory) {
            player.sendOpenInventory()
            return 1
        } else {
            val inventoryScreen = InventoryScreen(player)
            scheduleScreenChange(inventoryScreen)
            return 1
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val openBuilder = literal<Any?>("open")
        val openAction = openBuilder.executes { context -> openInventory(context) }
        root.then(openAction)

        val closeBuilder = literal<Any?>("close")
        val closeAction = closeBuilder.executes { context -> closeInventory(context) }
        root.then(closeAction)

        val clickBuilder = literal<Any?>("click")

        val slotBuilder = argument("slot", IntegerArgumentType.integer()) as RequiredArgumentBuilder<Any?, *>
        val buttonBuilder = argument("button", IntegerArgumentType.integer(0)) as RequiredArgumentBuilder<Any?, *>
        val typeBuilder = argument("type", EnumArgumentType(ClickAction::class.java)) as RequiredArgumentBuilder<Any?, *>

        val typeAction = typeBuilder.executes { context -> clickInventory(context) }
        val buttonWithType = buttonBuilder.then(typeAction)
        val slotWithButton = slotBuilder.then(buttonWithType)
        val clickWithSlot = clickBuilder.then(slotWithButton)
        root.then(clickWithSlot)
    }

    private fun clickInventory(context: CommandContext<*>): Int {
        val slot = context.getArgument("slot", Int::class.javaObjectType)
        val button = context.getArgument("button", Int::class.javaObjectType)
        val clickAction = context.getArgument("type", ClickAction::class.java) as ClickAction
        val clickType = clickAction.clickType

        try {
            InventoryUtils.clickSlot(slot, button, clickType)
        } catch (failure: Throwable) {
            val message = StringBuilder("Failed to click in inventory due to: ")
            if (failure is ReportedException) {
                val cause = failure.cause
                message.append(cause?.message ?: failure.message)
            } else {
                message.append(failure.message)
            }

            val failureMessage = Component.nullToEmpty(message.toString())
            ChatUtils.printChat(failureMessage, ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.HIGH)
        }

        return 1
    }

    private fun closeInventory(context: CommandContext<*>): Int {
        ClientAccess.collectiveMinecraft.setScreen(null)
        return 1
    }

    enum class ClickAction(val clickType: ClickType, private val argumentName: String) : NamedEntry {
        PICKUP(ClickType.PICKUP, "pickup"),
        QUICK_MOVE(ClickType.QUICK_MOVE, "quick_move"),
        SWAP(ClickType.SWAP, "swap"),
        CLONE(ClickType.CLONE, "clone"),
        THROW(ClickType.THROW, "throw"),
        QUICK_CRAFT(ClickType.QUICK_CRAFT, "quick_craft"),
        PICKUP_ALL(ClickType.PICKUP_ALL, "pickup_all");

        override fun getName(): String = argumentName
    }
}
