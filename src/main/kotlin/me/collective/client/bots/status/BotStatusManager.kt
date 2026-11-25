package me.collective.client.bots.status

import me.collective.client.bots.BotClient
import me.collective.client.bots.BotManager
import me.collective.client.bots.BotRole
import java.util.concurrent.ConcurrentHashMap

object BotStatusManager {
    private val buyerBots: MutableSet<BotClient> = ConcurrentHashMap.newKeySet()

    fun tick() {
        buyerBots.clear()
        for (bot in BotManager.bots) {
            if (bot.isOnline() && bot.profile.role == BotRole.BUYER) {
                buyerBots.add(bot)
            }
        }
    }

    fun unregisterBot(bot: BotClient) {
        buyerBots.remove(bot)
    }
}
