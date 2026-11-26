package me.collective.client.modules.combat

import java.util.Collections
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.state.BlockState

class BlockUpdateBatch(packet: Packet<*>) : Iterable<BlockUpdateBatch.Update> {
    private val updates: Iterable<Update> = extractUpdates(packet)

    override fun iterator(): Iterator<Update> = updates.iterator()

    @JvmRecord
    data class Update(val position: BlockPos, val state: BlockState)

    companion object {
        private const val UNSUPPORTED_PACKET_MESSAGE = "packet does not have any block update info"

        @JvmStatic
        fun isSupportedPacket(packet: Packet<*>): Boolean {
            return packet is ClientboundSectionBlocksUpdatePacket || packet is ClientboundBlockUpdatePacket
        }

        private fun extractUpdates(packet: Packet<*>): Iterable<Update> {
            return when (packet) {
                is ClientboundBlockUpdatePacket -> {
                    val singlePosition = packet.pos.immutable()
                    val singleUpdate = Update(singlePosition, packet.blockState)
                    Collections.singleton(singleUpdate)
                }
                is ClientboundSectionBlocksUpdatePacket -> {
                    val list = ArrayList<Update>()
                    packet.runUpdates { position, state ->
                        list.add(Update(position.immutable(), state))
                    }
                    list
                }
                else -> throw IllegalArgumentException(UNSUPPORTED_PACKET_MESSAGE)
            }
        }
    }
}
