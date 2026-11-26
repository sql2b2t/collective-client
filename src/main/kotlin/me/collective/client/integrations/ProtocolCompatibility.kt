package me.collective.client.integrations

import me.collective.client.utils.network.PacketSender

import me.collective.client.ClientAccess

import com.viaversion.viafabricplus.ViaFabricPlus
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.protocol.packet.PacketType
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.api.type.types.VarIntType
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

class ProtocolCompatibility {
   companion object {
      private val NATIVE_VERSION: ProtocolVersionAccess = object : ProtocolVersionAccess {
         override fun olderEq_1_20_6(): Boolean {
            return false
         }

         override fun olderEq_1_21_4(): Boolean {
            return false
         }

         override fun olderEq_1_21_5(): Boolean {
            return false
         }
      }
      private var targetVersion: ProtocolVersionAccess = NATIVE_VERSION

      @JvmStatic
      fun getTargetVersion(): ProtocolVersionAccess {
         return targetVersion
      }

      @JvmStatic
      fun setSneaking(sneaking: Boolean) {
         if (getTargetVersion().olderEq_1_21_5()) {
            LegacyPackets.setSneaking(sneaking)
         } else {
            val player = ClientAccess.collectiveMinecraft.player!!
            val input: Input = player!!.input.keyPresses
            val packet = ServerboundPlayerInputPacket(
               Input(
                  input.forward, input.backward, input.left, input.right,
                  input.jump, sneaking, input.sprint
               )
            )
            PacketSender.send(packet)
         }
      }

      @JvmStatic
      fun setTargetVersion(version: ProtocolVersionAccess?) {
         targetVersion = version ?: NATIVE_VERSION
      }
   }

   private object LegacyPackets {
      fun setSneaking(sneaking: Boolean) {
         val connection: UserConnection? = ViaFabricPlus.getImpl().playNetworkUserConnection
         val packetType: PacketType = ServerboundPackets1_21_5.PLAYER_COMMAND
         val packet = PacketWrapper.create(packetType, connection)
         val entityIdType: VarIntType = Types.VAR_INT
         val player = ClientAccess.collectiveMinecraft.player!!
         val entityId = player!!.id
         packet.write(entityIdType, entityId)
         val actionType: VarIntType = Types.VAR_INT
         val action = if (sneaking) 0 else 1
         packet.write(actionType, action)
         val dataType: VarIntType = Types.VAR_INT
         val data: Byte = 0
         packet.write(dataType, data.toInt())
         packet.scheduleSendToServer(Protocol1_21_5To1_21_6::class.java)
      }
   }
}
