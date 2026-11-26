package me.collective.client.access

import me.collective.client.bots.proxy.ProxyConfig
import net.minecraft.network.protocol.Packet

interface BotConnectionAccess {
    var isBotConnection: Boolean
    var proxy: ProxyConfig?

    fun sendPacket(packet: Packet<*>)
}
