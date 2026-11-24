package me.collective.client.utils.movement

import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.ClientPacketListenerHandleSetEntityMotionEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.FishingHookPullEntityEvent
import me.collective.client.modules.movement.VelocityModule

import java.util.Comparator
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.phys.AABB

open class GrimVelocityHandler(velocityModule: VelocityModule) : VelocityModeHandler(velocityModule) {
   private var intersectsNonEmptyBlock: Boolean = false
   private var compensationPending: Boolean = false
   private var lastSentYaw: Float = 0.0F
   private var lastSentPitch: Float = 0.0F

   private fun getPlayerIntersectionSize(blockPos: BlockPos): Float {
      val player = ClientAccess.collectiveMinecraft.player ?: return 0.0f
      val blockBounds = AABB(blockPos)
      return player.boundingBox.intersect(blockBounds).size.toFloat()
   }

   private fun sendBlockCompensation() {
      val target = findCompensationBlock()
      if (target == null || (!compensationPending && velocityModule.reduceSetting.value)) {
         return
      }
      compensationPending = false
      val player = ClientAccess.collectiveMinecraft.player ?: return
      PacketSender.sendPositionRotation(player.x, player.y, player.z, lastSentYaw, lastSentPitch, player.onGround())
      PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, target, Direction.DOWN)
   }

   private fun findCompensationBlock(): BlockPos? {
      val player = ClientAccess.collectiveMinecraft.player ?: return null
      val bounds = player.boundingBox
      return BlockPos.betweenClosedStream(bounds)
         .map { it.immutable() }
         .filter { position -> WorldUtils.isAir(position) ||
         (WorldUtils.hasFluid(position) && velocityModule.reduceSetting.value) }
         .max(Comparator.comparingDouble { position -> getPlayerIntersectionSize(position).toDouble() })
         .orElse(null)
   }

   private fun shouldLeaveMotionUnchanged(): Boolean {
      val noCompensationBlock = findCompensationBlock() == null && !intersectsNonEmptyBlock
      val player = ClientAccess.collectiveMinecraft.player ?: return true
      return player.isFallFlying || PlayerStateUtils.isInCobweb(player) || noCompensationBlock ||
         !ClientContext.serverStateManager.hasElapsedSinceSetback(100L)
   }

   override fun onEntityMotion(
      event: ClientPacketListenerHandleSetEntityMotionEvent
   ) {
      val entityId = event.entityId
      val player = ClientAccess.collectiveMinecraft.player ?: return

      if (entityId == player.id && !shouldLeaveMotionUnchanged()) {
         event.cancel()
         compensationPending = true
      }
   }

   override fun onFishingPull(event: FishingHookPullEntityEvent) {
      if (velocityModule.fishingBobberSetting.value && event.entity === ClientAccess.collectiveMinecraft.player!!) {
         compensationPending = true
      }
   }

   override fun onSendPacket(
      event: PacketEvent.Send
   ) {
      val packet = event.packet
      if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
         lastSentYaw = packet.getYRot(0.0F)
         lastSentPitch = packet.getXRot(0.0F)
      }
   }

   override fun onExplosion(
      event: ClientPacketListenerHandleExplosionEvent
   ) {
      val hasMotion = event.velocityX != 0.0F ||
         event.velocityY != 0.0F ||
         event.velocityZ != 0.0F
      if (hasMotion && velocityModule.explosionsSetting.value && !shouldLeaveMotionUnchanged()) {
         event.velocityX = 0.0F
         event.velocityY = 0.0F
         event.velocityZ = 0.0F
      }
   }

   override fun onTick() {
      val player = ClientAccess.collectiveMinecraft.player ?: return
      val bounds = player.boundingBox
      val level = ClientAccess.collectiveMinecraft.level ?: return
      intersectsNonEmptyBlock = BlockPos.betweenClosedStream(bounds)
         .anyMatch { position -> !level.isEmptyBlock(position) }
      if (player.tickCount > 20 && !shouldLeaveMotionUnchanged()) {
         sendBlockCompensation()
      }
   }

}
