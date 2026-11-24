package me.collective.client.bots.control

import io.netty.channel.Channel
import me.collective.client.access.BotConnectionAccess
import net.minecraft.network.Connection
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.BrandPayload
import net.minecraft.resources.Identifier
import java.util.EnumMap

object BotCustomPayloadCache {
    private const val MAX_ENTRIES: Int = 16
    private const val PLAY_DELAY_MS: Long = 500L
    private val cache: MutableMap<ConnectionProtocol, MutableMap<Identifier, ServerboundCustomPayloadPacket>> = EnumMap(ConnectionProtocol::class.java)
    private var lastConnection: Connection? = null

    fun record(connection: Connection, packet: ServerboundCustomPayloadPacket) {
        val proto = getProtocol(connection) ?: return
        if (packet.payload() is BrandPayload) return
        val id = packet.payload().type().id()

        synchronized(cache) {
            if (lastConnection !== connection) {
                cache.clear()
                lastConnection = connection
            }
            val map = cache.computeIfAbsent(proto) { LinkedHashMap() }
            if (map.size < MAX_ENTRIES || map.containsKey(id)) {
                map[id] = packet
            }
        }
    }

    fun replay(channel: Channel?, connection: Connection, protocol: ConnectionProtocol) {
        val packets: List<ServerboundCustomPayloadPacket>
        synchronized(cache) {
            val map = cache[protocol]
            if (map.isNullOrEmpty()) return
            packets = ArrayList(map.values)
        }

        val delay = if (protocol == ConnectionProtocol.CONFIGURATION) 0L else PLAY_DELAY_MS
        for (packet in packets) {
            BotPacketDispatcher.schedule(channel, {
                if (connection.isConnected && getProtocol(connection) == protocol && (connection as? BotConnectionAccess)?.isBotConnection == true) {
                    connection.send(packet)
                }
            }, delay)
        }
    }

    private fun getProtocol(connection: Connection): ConnectionProtocol? {
        val listener = connection.packetListener ?: return null
        val proto = listener.protocol()
        return if (proto == ConnectionProtocol.CONFIGURATION || proto == ConnectionProtocol.PLAY) proto else null
    }
}
