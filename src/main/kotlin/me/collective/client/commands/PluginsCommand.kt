package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.tree.CommandNode
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.PacketEvent
import me.collective.client.render.ColorUtils
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.network.PacketSender
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket

class PluginsCommand : Command("plugins") {
    private final val stopwatch = ElapsedTimer()
    private var queryPending = false

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            PacketSender.send(ServerboundCommandSuggestionPacket(0, "/"))

            val fetchingMessage = Component.literal("Fetching plugins...")
            ChatUtils.printChat(fetchingMessage, ChatUtils.createSignature(-3))
            this.stopwatch.reset()
            this.queryPending = true
            1
        }
    }

    private fun formatPlugins(commandNames: Iterable<String>): Component {
        val namespaces = HashSet<String>()
        for (commandName in commandNames) {
            val parts = commandName.split(":")
            if (parts.size > 1 && parts[0].isNotEmpty()) {
                namespaces.add(parts[0])
            }
        }

        val pluginList = ComponentUtils.formatList(namespaces) { pluginName: String ->
            Component.literal(pluginName).withStyle { style ->
                ChatUtils.withDynamicColor(style) { ColorUtils.getThemeColor().hashCode() }
            }
        }
        val count = Component.literal(namespaces.size.toString()).withStyle(ChatFormatting.GRAY)
        return Component.empty()
            .append("Found plugins [")
            .append(count)
            .append("]: ")
            .append(pluginList)
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        if (this.queryPending) {
            if (this.stopwatch.hasElapsed(3000L)) {
                this.queryPending = false
                val player = ClientAccess.collectiveMinecraft.player
                val connection = player?.connection
                if (connection != null) {
                    val dispatcher = connection.commands
                    val commandRoot = dispatcher.root

                    val commandNames = commandRoot.children.stream()
                        .map(CommandNode<*>::getName)
                        .filter { commandName -> !commandName.contains("fabric-command-api-v2") }
                        .toList()
                    if (commandNames.isEmpty()) {
                        val failureMessage = Component.literal("Couldn't get plugins")
                        ChatUtils.printChat(failureMessage, ChatUtils.createSignature(-3))
                    } else {
                        val fallbackMessage = this.formatPlugins(commandNames)
                        ChatUtils.printChat(fallbackMessage, ChatUtils.createSignature(-3))
                    }
                }
            }

            val packet = event.packet
            if (packet is ClientboundCommandSuggestionsPacket) {
                this.queryPending = false
                val suggestions = packet.toSuggestions()
                val suggestionTexts = suggestions.list.map(Suggestion::getText)
                val suggestionMessage = this.formatPlugins(suggestionTexts)
                ChatUtils.printChat(suggestionMessage, ChatUtils.createSignature(-3))
            }
        }
    }
}
