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
         val buffer = FriendlyByteBuf(Unpooled.buffer())
         packet.write(buffer)
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return null
         val entityId = buffer.readVarInt()
         return level.getEntity(entityId)
      }

      @JvmStatic
      fun getAction(packet: ServerboundInteractPacket): Action {
         val buffer = FriendlyByteBuf(Unpooled.buffer())
         packet.write(buffer)
         buffer.readVarInt()
         val actionType: Class<Action> = Action::class.java
         return buffer.readEnum(actionType)
      }
   }

   enum class Action {
      INTERACT,
      ATTACK,
      INTERACT_AT
   }
}
