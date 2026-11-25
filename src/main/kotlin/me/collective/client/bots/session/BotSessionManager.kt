package me.collective.client.bots.session

import me.collective.client.ClientAccess
import me.collective.client.bots.BotClient
import net.minecraft.client.User

object BotSessionManager : ClientAccess {
    @Volatile var controlledBot: BotClient? = null
    @Volatile var originalUser: User? = null

    val isControllingBot: Boolean get() = controlledBot != null

    fun isBotControlled(bot: BotClient?): Boolean {
        return bot != null && controlledBot === bot
    }

    fun tick() {
        val bot = controlledBot ?: return
        if (!bot.isOnline()) {
            controlledBot = null
        }
    }
}
