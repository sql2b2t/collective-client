package me.collective.client.events

import net.minecraft.network.protocol.Packet

sealed class PacketEvent protected constructor(val packet: Packet<*>) : CancellableEvent() {
    class Send(packet: Packet<*>) : PacketEvent(packet)

    class Receive(packet: Packet<*>) : PacketEvent(packet)

    class PostSend(packet: Packet<*>) : PacketEvent(packet)
}
