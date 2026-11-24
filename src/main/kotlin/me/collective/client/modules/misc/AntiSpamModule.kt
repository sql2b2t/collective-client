package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.access.VisibleChatLinesAccess
import me.collective.client.events.ChatMessageEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils

import java.util.HashMap
import java.util.Locale
import java.util.regex.Pattern
import net.minecraft.client.GuiMessage.Line
import net.minecraft.network.chat.Component

class AntiSpamModule : Module("AntiSpam", "Stacks similar chat messages with each other.", ModuleCategory.MISC) {
    private val messageStacks: HashMap<String, MessageStack> = HashMap()
    val indicatorSetting: Setting<Boolean>
    private val linksSetting: Setting<Boolean>
    private var pendingMessage: String? = null

    init {
        indicatorSetting = add(BooleanSetting("Indicator", true))
        linksSetting = add(BooleanSetting("Links", false))
        setDrawn(false)
    }

    override fun onToggle() {
        messageStacks.clear()
        pendingMessage = null
    }

    @EventHandler
    private fun onDisconnect(event: MinecraftDisconnectEvent) {
        messageStacks.clear()
        pendingMessage = null
    }

    @EventHandler(priority = 200)
    private fun onChatLine(event: ChatMessageEvent) {
        if (event.stage == EventStage.Pre) {
            if (event.signature != null && event.signature!!.asByteBuffer().getInt() < 0) {
                return
            }
            if (event.tag ==
         ChatUtils.MESSAGE_TAG
            ) {
                return
            }
            val messageKey: String = event.message!!.getString().lowercase()
            pendingMessage = messageKey
            if (linksSetting.value && containsLink(messageKey)) {
                event.cancel()
                return
            }
            if (indicatorSetting.value) {
                // У Pre-событий line == null — раньше event.line!! давал NPE и индикатор
                // повторов не работал вовсе (исключение глушится шиной событий).
                messageStacks.computeIfPresent(messageKey) { message, stack ->
                    val previousLine = stack.line
                    if (previousLine != null) {
                        (ClientAccess.collectiveMinecraft.gui.chat as VisibleChatLinesAccess).getVisible().remove(previousLine)
                    }
                    stack
                }
                messageStacks.putIfAbsent(messageKey, MessageStack(1L, event.line))
                val count: Long = messageStacks[messageKey]!!.count
                val msg = event.message
                if (count > 1L && msg != null) {
                    event.message = Component.empty()
                        .append(msg)
                        .append(Component.literal(" [x$count]"))
                }
            }
        } else if (pendingMessage != null && indicatorSetting.value) {
            messageStacks.computeIfPresent(pendingMessage!!) { message, stack -> MessageStack(stack.count + 1L,
                event.line ?: stack.line) }
        }
    }

    private fun containsLink(message: String): Boolean {
        return URL_PATTERN.matcher(message).find() || INVITE_PATTERN.matcher(message).find()
    }

    private data class MessageStack(val count: Long, val line: Line?)

    companion object {
        private val URL_PATTERN: Pattern = Pattern.compile("(https?://)?([\\w\\-])+\\.{1}([a-zA-Z]{2,63})([/\\w-]*)*/?\\??([^#\\n\\r]*)?#?([^\\n\\r]*)")
        private val INVITE_PATTERN: Pattern = Pattern.compile("gg/([a-zA-Z0-9]*)")
    }
}
