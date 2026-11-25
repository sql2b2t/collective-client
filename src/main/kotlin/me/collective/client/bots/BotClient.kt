package me.collective.client.bots

import com.mojang.brigadier.tree.RootCommandNode
import io.netty.channel.Channel
import io.netty.channel.EventLoop
import me.collective.client.ClientAccess
import me.collective.client.bots.control.BotContainerController
import me.collective.client.bots.control.BotInputController
import me.collective.client.bots.control.BotInteractionController
import me.collective.client.bots.network.BotConnectionFactory
import me.collective.client.bots.world.BotPlayerState
import me.collective.client.bots.world.BotWorldState
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.Connection
import net.minecraft.network.chat.LastSeenMessages
import net.minecraft.network.chat.LastSeenMessagesTracker
import net.minecraft.network.chat.MessageSignatureCache
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundChatPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.Util
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BotClient(
    val profile: BotProfile
) : ClientAccess {
    val playerState: BotPlayerState = BotPlayerState()
    val worldState: BotWorldState = BotWorldState()
    val inputController: BotInputController = BotInputController(this)
    val interactionController: BotInteractionController = BotInteractionController(this)
    val containerController: BotContainerController = BotContainerController(this)

    @Volatile var connectionState: BotConnectionState = BotConnectionState.IDLE
    @Volatile var disconnectReason: String = ""

    @Volatile var connection: Connection? = null
    @Volatile var channel: Channel? = null
    val eventLoop: EventLoop? get() = channel?.eventLoop()

    private val cookies: MutableMap<ResourceLocation, ByteArray> = ConcurrentHashMap()
    @Volatile var commandDispatcher: RootCommandNode<SharedSuggestionProvider>? = null

    @Volatile var lastKeepAliveId: Long = 0L
    @Volatile var isTransferred: Boolean = false
    @Volatile var autoReconnect: Boolean = true
    @Volatile var nextReconnectTimeMs: Long = 0L
    @Volatile var lastTickTimeMs: Long = System.currentTimeMillis()

    private val isConnecting = AtomicBoolean(false)
    private val pendingTickCount = AtomicInteger(0)
    private var isPlayerLoadedSent: Boolean = false
    private var loadWaitTicks: Int = 0

    val lastSeenMessagesTracker: LastSeenMessagesTracker = LastSeenMessagesTracker(20)
    val messageSignatureCache: MessageSignatureCache = MessageSignatureCache.createDefault()

    @Volatile var connectTimeMs: Long = 0L
    @Volatile var ping: Int = 0

    val health: Float get() = playerState.health
    val foodLevel: Int get() = playerState.foodLevel

    val uptimeFormatted: String get() {
        if (!isOnline() || connectTimeMs == 0L) return "—"
        val seconds = (System.currentTimeMillis() - connectTimeMs) / 1000
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    init {
        worldState.ownerBot = this
    }

    fun isOnline(): Boolean = connectionState.isInGame && connection?.isConnected == true

    fun attachConnection(conn: Connection, chan: Channel) {
        connection = conn
        channel = chan
        connectionState = BotConnectionState.CONNECTING
        isConnecting.set(false)
        isPlayerLoadedSent = false
        loadWaitTicks = 0
    }

    fun connect() {
        if (!autoReconnect && connectionState == BotConnectionState.IDLE) return
        if (connectionState.isActive && connectionState != BotConnectionState.QUEUED) return

        connectionState = BotConnectionState.CONNECTING
        isConnecting.set(true)
        worldState.reset()
        playerState.reset()

        Util.ioPool().execute {
            try {
                BotConnectionFactory.connect(this)
            } catch (t: Throwable) {
                isConnecting.set(false)
                disconnectReason = t.message ?: "Failed to connect"
                disconnect(disconnectReason)
            }
        }
    }

    fun disconnect(reason: String = "Disconnected") {
        disconnectReason = reason
        connectionState = BotConnectionState.DISCONNECTED
        val conn = connection
        connection = null
        channel = null
        isConnecting.set(false)

        try {
            conn?.disconnect(net.minecraft.network.chat.Component.literal(reason))
        } catch (ignored: Throwable) {}

        if (autoReconnect) {
            nextReconnectTimeMs = System.currentTimeMillis() + 5000L
            connectionState = BotConnectionState.QUEUED
        }
    }

    fun sendPacket(packet: Packet<*>) {
        val conn = connection
        if (conn != null && conn.isConnected) {
            conn.send(packet)
        }
    }

    fun sendChat(message: String) {
        if (!isOnline()) return
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return

        val update = lastSeenMessagesTracker.generateAndApplyUpdate()?.update()
            ?: LastSeenMessages.Update(0, java.util.BitSet())
        sendPacket(ServerboundChatPacket(trimmed, Instant.now(), 0L, null, update))
    }

    fun onChatMessageReceived(text: String) {
        // Trigger auto-buy or command reactions if applicable
    }

    fun setCookie(key: ResourceLocation, data: ByteArray) {
        cookies[key] = data
    }

    fun getCookie(key: ResourceLocation): ByteArray? = cookies[key]

    fun handleTransfer(host: String, port: Int) {
        isTransferred = true
        profile.serverAddress = "$host:$port"
        disconnect("Transferring to $host:$port")
        connect()
    }

    fun tick() {
        val conn = connection ?: return
        if (!conn.isConnected) {
            if (connectionState.isActive) {
                disconnect("Connection closed")
            }
            return
        }

        lastTickTimeMs = System.currentTimeMillis()

        try {
            conn.tick()
        } catch (ignored: Throwable) {}

        if (connectionState == BotConnectionState.IN_GAME) {
            try {
                tickGame(conn)
            } catch (ignored: Throwable) {}
        }
    }

    private fun tickGame(conn: Connection) {
        if (!isPlayerLoadedSent) {
            loadWaitTicks++
            if (loadWaitTicks >= 25) {
                isPlayerLoadedSent = true
                conn.send(ServerboundPlayerLoadedPacket())
            }
        }

        worldState.botClientLevel?.tickBookkeeping()

        val player = worldState.botLocalPlayer
        if (player != null) {
            inputController.tick()
            player.physicsTick()
            interactionController.tick()
            containerController.tick()
        }

        conn.send(ServerboundClientTickEndPacket.INSTANCE)
    }
}
