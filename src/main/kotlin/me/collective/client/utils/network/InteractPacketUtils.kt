package me.collective.client.utils.network

import me.collective.client.ClientAccess
import io.netty.buffer.Unpooled
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.entity.Entity

class InteractPacketUtils private constructor() : ClientAccess {

   companion object {

      @JvmStatic
      fun getEntity(packet: ServerboundInteractPacket): Entity? {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return null
         return level.getEntity(packet.entityId())
      }

      @JvmStatic
      fun getAction(packet: ServerboundInteractPacket): Action {
         return if (packet.location() != net.minecraft.world.phys.Vec3.ZERO) Action.INTERACT_AT else Action.INTERACT
      }
   }

   enum class Action {
      INTERACT,
      ATTACK,
      INTERACT_AT
   }
}
