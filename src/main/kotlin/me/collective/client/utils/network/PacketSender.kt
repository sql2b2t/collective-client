package me.collective.client.utils.network

import me.collective.client.ClientAccess
import me.collective.client.ClientContext

import io.netty.buffer.Unpooled
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.Connection
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult

class PacketSender private constructor() : ClientAccess {

   companion object {

      @JvmStatic
      fun sendUseItemOn(hand: InteractionHand, hitResult: BlockHitResult) {
         send(
            ServerboundUseItemOnPacket(hand, hitResult, nextPredictionSequence())
         )
      }

      @JvmStatic
      fun sendUseItem(hand: InteractionHand) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         val yaw = player.yRot
         val pitch = player.xRot
         sendUseItem(hand, yaw, pitch)
      }

      @JvmStatic
      fun sendUseItem(hand: InteractionHand, yaw: Float, pitch: Float) {
         send(
            ServerboundUseItemPacket(hand, nextPredictionSequence(), yaw, pitch)
         )
      }

      @JvmStatic
      fun sendSwing(hand: InteractionHand) {
         send(ServerboundSwingPacket(hand))
      }

      @JvmStatic
      fun sendRotation(yaw: Float, pitch: Float, onGround: Boolean) {
         send(
            Rot(yaw, pitch, onGround, ClientAccess.collectiveMinecraft.player?.horizontalCollision ?: return)
         )
      }

      @JvmStatic
      fun sendRotation(rotation: FloatArray?, onGround: Boolean) {
         if (rotation != null) {
            val angleCount = rotation.size

            if (angleCount >= 2) {
               val yaw = rotation[0]
               val pitch = rotation[1]
               sendRotation(yaw, pitch, onGround)
            }
         }
      }

      @JvmStatic
      fun sendPosition(
         x: Double, y: Double, z: Double, onGround: Boolean
      ) {
         send(
            Pos(x, y, z, onGround, ClientAccess.collectiveMinecraft.player?.horizontalCollision ?: return)
         )
      }

      @JvmStatic
      fun sendPositionRotation(
         x: Double, y: Double, z: Double, yaw: Float, pitch: Float, onGround: Boolean
      ) {
         send(
            PosRot(
               x, y, z, yaw, pitch, onGround, ClientAccess.collectiveMinecraft.player?.horizontalCollision ?: return
            )
         )
      }

      @JvmStatic
      fun sendAttack(entityId: Int) {
         send(net.minecraft.network.protocol.game.ServerboundAttackPacket(entityId))
      }

      @JvmStatic
      fun sendPlayerAction(
         action: Action, position: BlockPos, direction: Direction
      ) {
         val sequenceSnapshot: Int = if (action == Action.ABORT_DESTROY_BLOCK) {
            0
         } else {
            nextPredictionSequence()
         }

         send(ServerboundPlayerActionPacket(action, position, direction, sequenceSnapshot))
      }

      @JvmStatic
      fun markImportant(packet: Packet<*>) {
         if (packet is ImportantPacket) {
            packet.`collective$markImportant`()
         }
      }

      @JvmStatic
      fun sendPlayerCommand(action: ServerboundPlayerCommandPacket.Action) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         sendPlayerCommand(player, action)
      }

      @JvmStatic
      fun sendPlayerCommand(
         entity: Entity, action: ServerboundPlayerCommandPacket.Action
      ) {
         send(ServerboundPlayerCommandPacket(entity, action, 0))
      }

      @JvmStatic
      fun sendStartFallFlying() {
         val action = ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
         val player = ClientAccess.collectiveMinecraft.player ?: return
         sendPlayerCommand(player, action)
      }

      @JvmStatic
      fun sendSwapOffhand() {
         send(ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN))
      }

      @JvmStatic
      fun sendSelectedSlot(slot: Int) {
         send(ServerboundSetCarriedItemPacket(slot))
      }

      @JvmStatic
      fun send(packet: Packet<*>) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         player.connection.send(packet)
      }

      @JvmStatic
      fun nextPredictionSequence(): Int {
         return ClientContext.worldStateManager.nextPredictionSequence()
      }

      @JvmStatic
      fun sendDirect(packet: Packet<*>?) {
         if (packet != null) {
            val connection: Connection = ClientAccess.collectiveMinecraft.player?.connection?.connection ?: return
            if (connection.isConnected) {
               connection.sentPackets++
               if (connection.channel.eventLoop().inEventLoop()) {
                  connection.channel.writeAndFlush(packet)
               } else {
                  connection.channel.eventLoop().execute { connection.channel.writeAndFlush(packet) }
               }
            }
         }
      }

   }

}
