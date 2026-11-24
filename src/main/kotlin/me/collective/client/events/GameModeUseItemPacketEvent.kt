package me.collective.client.events

import net.minecraft.network.protocol.game.ServerboundUseItemPacket

class GameModeUseItemPacketEvent(packet: ServerboundUseItemPacket) : CancellableEvent() {
    var packet: ServerboundUseItemPacket = packet

    fun setRotations(rotations: FloatArray) {
        packet = ServerboundUseItemPacket(packet.hand, packet.sequence, rotations[0], rotations[1])
        cancel()
    }
}
