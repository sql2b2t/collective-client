package me.collective.client.bots

import me.collective.client.ClientAccess
import me.collective.client.bots.follow.BotFollowManager
import me.collective.client.bots.session.BotSessionManager
import me.collective.client.bots.status.BotStatusManager
import me.collective.client.bots.storage.BotSettingsStorage
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.bus.EventBus
import net.minecraft.network.Connection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object BotManager : ClientAccess {
    const val MAX_BOTS: Int = 512

    val bots: CopyOnWriteArrayList<BotClient> = CopyOnWriteArrayList()
    val connectQueue: ConcurrentLinkedQueue<BotClient> = ConcurrentLinkedQueue()
    private val isQueueProcessing = AtomicBoolean(false)

    var connectDelayMs: Long = 400L
    var viewDistance: Int = 4
    var autoReconnect: Boolean = true
    var autoRespawn: Boolean = true
    var chunkLoading: Boolean = true

    fun init() {
        ClientAccess.eventBus.subscribe(this)
        BotSettingsStorage.load()
    }

    @EventHandler
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        tick()
    }

    fun addBot(name: String, address: String, proxyId: String = ""): BotClient? {
        val trimmedName = name.trim()
        val trimmedAddress = me.collective.client.bots.network.BotConnectionFactory.normalizeAddress(address)
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return null
        if (findByName(trimmedName) != null) return null
        if (bots.size >= MAX_BOTS) return null

        val profile = BotProfile(trimmedName, trimmedAddress, proxyId)
        val bot = BotClient(profile)
        bots.add(bot)
        BotSettingsStorage.save()
        return bot
    }

    fun addBot(profile: BotProfile): BotClient? {
        if (findByName(profile.username) != null) return null
        if (bots.size >= MAX_BOTS) return null

        val bot = BotClient(profile)
        bots.add(bot)
        BotSettingsStorage.save()
        return bot
    }

    fun addBots(name: String, count: Int, address: String, proxyId: String = ""): List<BotClient> {
        val list = mutableListOf<BotClient>()
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return list

        if (count <= 1) {
            addBot(trimmedName, trimmedAddress, proxyId)?.let { list.add(it) }
        } else {
            for (i in 1..count) {
                val botName = "$trimmedName$i"
                addBot(botName, trimmedAddress, proxyId)?.let { list.add(it) }
            }
        }
        return list
    }

    fun removeBot(bot: BotClient) {
        bot.disconnect("Removed from bot list")
        bots.remove(bot)
        connectQueue.remove(bot)
        BotFollowManager.unregisterBot(bot)
        BotStatusManager.unregisterBot(bot)
        BotSettingsStorage.save()
    }

    fun findByName(name: String): BotClient? {
        for (bot in bots) {
            if (bot.profile.username.equals(name, ignoreCase = true)) {
                return bot
            }
        }
        return null
    }

    fun findByConnection(connection: Connection): BotClient? {
        for (bot in bots) {
            if (bot.connection === connection) {
                return bot
            }
        }
        val controlled = BotSessionManager.controlledBot
        if (controlled?.connection === connection) {
            return controlled
        }
        return null
    }

    fun connectAll() {
        for (bot in bots) {
            if (bot.connectionState != BotConnectionState.CONTROLLED) {
                enqueueConnect(bot)
            }
        }
    }

    fun disconnectAll() {
        connectQueue.clear()
        for (bot in bots) {
            bot.disconnect("Disconnected all bots")
        }
    }

    fun enqueueConnect(bot: BotClient) {
        if (!connectQueue.contains(bot)) {
            connectQueue.add(bot)
        }
        bot.connectionState = BotConnectionState.QUEUED
    }

    fun sendChatToAll(message: String): Int {
        val controlled = BotSessionManager.controlledBot
        var count = 0
        for (bot in bots) {
            if (bot !== controlled && bot.isOnline()) {
                bot.sendChat(message)
                count++
            }
        }
        return count
    }

    fun tick() {
        BotSessionManager.tick()
        BotFollowManager.tick()
        BotStatusManager.tick()

        processConnectQueue()

        for (bot in bots) {
            try {
                bot.tick()
            } catch (ignored: Throwable) {}
        }

        val controlled = BotSessionManager.controlledBot
        try {
            controlled?.tick()
        } catch (ignored: Throwable) {}
    }

    private var lastConnectTimeMs: Long = 0L

    private fun processConnectQueue() {
        val now = System.currentTimeMillis()
        if (now - lastConnectTimeMs < connectDelayMs) return

        val bot = connectQueue.poll() ?: return
        lastConnectTimeMs = now
        bot.connect()
    }
}
