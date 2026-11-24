package me.collective.client.bots.control

import io.netty.channel.Channel
import io.netty.channel.EventLoop
import me.collective.client.access.BotConnectionAccess
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket
import java.util.UUID
import java.util.concurrent.TimeUnit

object BotPacketDispatcher {
    const val DOWNLOAD_DELAY_MS: Long = 750L
    const val LOADED_DELAY_MS: Long = 1900L

    fun schedule(channel: Channel?, task: Runnable, delayMs: Long) {
        if (channel != null && channel.isOpen) {
            channel.eventLoop().schedule(task, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    fun scheduleDelayed(loop: EventLoop?, task: Runnable, delayMs: Long) {
        loop?.schedule(task, delayMs, TimeUnit.MILLISECONDS)
    }

    fun sendDelayed(channel: Channel?, connection: Connection, packet: Packet<*>, delayMs: Long) {
        schedule(channel, {
            if (connection.isConnected && (connection as? BotConnectionAccess)?.isBotConnection == true) {
                connection.send(packet)
            }
        }, delayMs)
    }

    fun handleResourcePack(channel: Channel?, connection: Connection, packId: UUID) {
        acknowledgeResourcePack(channel?.eventLoop(), connection, packId)
    }

    fun acknowledgeResourcePack(loop: EventLoop?, connection: Connection, packId: UUID) {
        connection.send(ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED))
        loop?.schedule({
            if (connection.isConnected) {
                connection.send(ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.DOWNLOADED))
            }
        }, DOWNLOAD_DELAY_MS, TimeUnit.MILLISECONDS)
        loop?.schedule({
            if (connection.isConnected) {
                connection.send(ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED))
            }
        }, LOADED_DELAY_MS, TimeUnit.MILLISECONDS)
    }
}
