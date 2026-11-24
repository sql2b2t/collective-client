package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.access.ChatLineAccess
import me.collective.client.access.VisibleChatLinesAccess
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import net.minecraft.client.GuiMessage.Line
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component

class PanicCommand : Command("panic") {

    private fun unload() {
        val configuration = ClientContext.configurationManager
        configuration.save()
        unloaded = true
        val previousPrefix = CommandRegistry.getPrefix()
        CommandRegistry.setPrefix(HIDDEN_PREFIX)
        val modules = ClientContext.moduleRegistry
        for (entry in modules.getRegistry()) {
            val module = entry as Module
            module.modifyKeybind { binding -> binding.withCode(-1) }
            if (module.isToggled) {
                module.toggle()
            }
        }

        val recentChatGui = ClientAccess.collectiveMinecraft.gui
        val recentChat = recentChatGui.chat
        recentChat.recentChat.removeIf { message -> message.startsWith(previousPrefix) }

        val visibleChatGui = ClientAccess.collectiveMinecraft.gui
        ((visibleChatGui.chat as Any) as VisibleChatLinesAccess).getVisible().removeIf { line: Any? ->
            val access = line as? ChatLineAccess ?: return@removeIf false
            val signature = access.getSignature()
            if (signature != null) {
                val signatureBytes = signature.asByteBuffer()
                if (signatureBytes.getInt() < 0) {
                    return@removeIf true
                }
            }
            false
        }

        val remainingChatGui = ClientAccess.collectiveMinecraft.gui
        val remainingChat = remainingChatGui.chat
        remainingChat.recentChat.removeIf { message -> message.startsWith(previousPrefix) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            scheduleRotationTask {
                val minecraft = ClientAccess.collectiveMinecraft
                val confirmationScreen = ConfirmScreen(
                    BooleanConsumer { confirmed ->
                        if (confirmed) {
                            unload()
                        }
                        ClientAccess.collectiveMinecraft.setScreen(null)
                    },
                    Component.literal("Unload the client."),
                    Component.literal("Continue?")
                )
                minecraft.setScreen(confirmationScreen)
            }
            1
        }
    }

    companion object {
        @JvmField
        var unloaded: Boolean = false

        private const val HIDDEN_PREFIX = "ㅤ"
    }
}
