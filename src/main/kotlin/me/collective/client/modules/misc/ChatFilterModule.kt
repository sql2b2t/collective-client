package me.collective.client.modules.misc

import me.collective.client.ClientContext
import me.collective.client.events.ChatMessageEvent
import me.collective.client.events.ClientPacketListenerSendChatEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils

import net.minecraft.client.GuiMessageTag
import net.minecraft.network.chat.Component

class ChatFilterModule : Module("ChatFilter", "Filters chat messages based on your filters.", ModuleCategory.MISC) {
    @JvmField
    val caseSensitiveSetting: Setting<Boolean>
    private val ignoreSelfSetting: Setting<Boolean>
    private val ignorePrivateMessagesSetting: Setting<Boolean>
    private var lastSentMessage: String? = null

    init {
        caseSensitiveSetting = add(BooleanSetting("CaseSensitive", false))
        ignoreSelfSetting = add(BooleanSetting("IgnoreSelf", true))
        ignorePrivateMessagesSetting = add(BooleanSetting("IgnorePM", false))
        lastSentMessage = null
    }

    override fun onEnable() {
        if (!isWorldUnavailable() && ClientContext.chatFilterRegistry.isEmpty()) {
            val notice = "You don't have any chat filters set. Use the \"${CommandRegistry.getPrefix()}chatfilter add <id> <filter>\" command to add new filters."
            ChatUtils.printChat(
                Component.nullToEmpty(notice),
                ChatUtils.moduleSignature(this),
                ChatUtils.MessagePriority.LOW)
        }
        lastSentMessage = null
    }

    @EventHandler(priority = 200)
    private fun onIncomingChat(event: ChatMessageEvent) {
        if (event.stage != EventStage.Pre ||
         event.message == null
        ) {
            return
        }
        val tag = event.tag
        if (tag != null && tag !== GuiMessageTag.system() && tag !== GuiMessageTag.systemSinglePlayer() && tag !== GuiMessageTag.chatNotSecure()) {
            return
        }
        val text: String? = event.message!!.getString()
        if (text == null) {
            return
        }
        if (ignoreSelfSetting.value && lastSentMessage != null && text.contains(lastSentMessage!!)) {
            lastSentMessage = null
            return
        }
        if (ignorePrivateMessagesSetting.value && ChatUtils.isIncomingWhisper(text)) {
            return
        }
        if (!ClientContext.chatFilterRegistry.isEmpty() &&
         ClientContext.chatFilterRegistry.matchesMessage(text)
        ) {
            event.cancel()
        }
    }

    @EventHandler
    private fun onOutgoingChat(event: ClientPacketListenerSendChatEvent) {
        if (!event.isCancelled &&
         ignoreSelfSetting.value
        ) {
            lastSentMessage = event.message
        }
    }

}
