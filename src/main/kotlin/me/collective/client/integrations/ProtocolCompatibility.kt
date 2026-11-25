package me.collective.client.integrations

import me.collective.client.utils.network.PacketSender

import me.collective.client.ClientAccess

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

class ProtocolCompatibility {
   companion object {
      private val NATIVE_VERSION: ProtocolVersionAccess = object : ProtocolVersionAccess {
         override fun olderEq_1_20_6(): Boolean {
            return false
         }

         override fun olderEq_1_21_4(): Boolean {
            return true
         }

         override fun olderEq_1_21_5(): Boolean {
            return true
         }
      }
      private var targetVersion: ProtocolVersionAccess = NATIVE_VERSION

      @JvmStatic
      fun getTargetVersion(): ProtocolVersionAccess {
         return targetVersion
      }

      @JvmStatic
      fun setSneaking(sneaking: Boolean) {
         val player = ClientAccess.collectiveMinecraft.player!!
         val action = if (sneaking) {
            ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
         } else {
            ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY
         }
         PacketSender.send(ServerboundPlayerCommandPacket(player, action))
      }

      @JvmStatic
      fun setTargetVersion(version: ProtocolVersionAccess?) {
         targetVersion = version ?: NATIVE_VERSION
      }
   }
}
