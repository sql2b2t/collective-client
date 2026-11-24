package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.access.LocalPlayerTickAccess
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.ClientPacketListenerHandleSetEntityMotionEvent
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.FastWebModule
import me.collective.client.modules.movement.SprintModule
import me.collective.client.modules.movement.VelocityModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants
import me.collective.mixin.LocalPlayerAccessor
import java.util.ArrayList
import java.util.Collections
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

class WallsVelocityHandler(velocityModule: VelocityModule) : VelocityModeHandler(velocityModule) {
   private val recentPositionCorrections: MutableList<Long> = Collections.synchronizedList(ArrayList())
   private val correctionCooldown: ElapsedTimer = ElapsedTimer()
   private var groundCollisionPresent: Boolean = false

   private fun getAbsolutePositionDeltaSum(): Float {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return 0.0f
      var deltaSum: Double = player.getX() - player.xo
      deltaSum = deltaSum + player.getY() - player.yo
      deltaSum = deltaSum + player.getZ() - player.zo
      return Math.abs(deltaSum).toFloat()
   }

   private fun canApplyWallVelocity(): Boolean {
      return this.isStationaryStaticMode() || this.groundCollisionPresent
   }

   private fun isStationaryStaticMode(): Boolean {
      return this.velocityModule.staticSetting.value && !PlayerStateUtils.isFallFlying() &&
         this.getAbsolutePositionDeltaSum() < 0.01
   }

   private fun applyYawAndPitch(event: LocalPlayerSendPositionEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val access: LocalPlayerAccessor = player as LocalPlayerAccessor
      val previousYaw: Float = access.lastYaw()
      var moving: Boolean = MovementPhysics.isLocalPlayerMoving()
      val yawWithinThreshold: Boolean = Mth.degreesDifferenceAbs(event.getCurrentYaw(), previousYaw) <= 2.0f
      if (sprintModule!!.isToggled() && sprintModule!!.shouldDeferToVanillaSprinting() &&
         !allOccupiedBlocksHaveCollision() && yawWithinThreshold) {
         moving = false
      }
      if (!moving) {
         if (this.isStationaryStaticMode()) {
            access.`collective$setTicksSinceLastPositionPacketSent`(Math.min(access.`collective$getTicksSinceLastPositionPacketSent`(), 18))
         }
         event.yaw = previousYaw
      }
      event.pitch = 89.0f
   }

   override fun onEntityMotion(
      event: ClientPacketListenerHandleSetEntityMotionEvent
   ) {
      val entityId: Int = event.entityId
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return

      if (entityId == player.getId() && this.shouldCancelVelocity()) {
         val incomingVelocity: Vec3 = event.getVelocity()
         val verticalSpeed: Double = Math.abs(incomingVelocity.y)
         if (verticalSpeed < 0.0788 && incomingVelocity.x == 0.0 && incomingVelocity.z == 0.0) {
            return
         }

         event.setVelocity(0.0f, 0.0f, 0.0f)
         event.cancel()
      }
   }

   override fun onExplosion(
      event: ClientPacketListenerHandleExplosionEvent
   ) {
      if (this.shouldCancelVelocity()) {
         event.velocityX = 0.0f
         event.velocityY = 0.0f
         event.velocityZ = 0.0f
      }
   }

   private fun applyRandomizedPitch(event: LocalPlayerSendPositionEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val access: LocalPlayerAccessor = player as LocalPlayerAccessor
      access.`collective$setTicksSinceLastPositionPacketSent`(20)
      val tickAccess: LocalPlayerTickAccess = player as LocalPlayerTickAccess
      tickAccess.`collective$updateServer`(Math.random().toFloat(), Math.random().toFloat())
      val jitter: Float = AntiCheatModule.ROTATION_JITTER_STEP * (player.tickCount % 6 - 3)
      event.pitch = Mth.clamp(event.getCurrentPitch() + jitter, (-MathConstants.NINETY_DEGREES).toFloat(), MathConstants.NINETY_DEGREES.toFloat())
   }

   fun shouldCancelVelocity(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      if (fastWebModule!!.isToggled() && fastWebModule!!.isGrimEnabled() &&
         PlayerStateUtils.isInCobweb(player)) {
         return true
      }
      return this.correctionCooldown.hasElapsed(150L) && this.canApplyWallVelocity()
   }

   override fun onReceivePacket(event: PacketEvent.Receive) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.recentPositionCorrections.add(System.currentTimeMillis())
      }
   }

   override fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Post == event.stage ||
         ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*>
         || !this.canApplyWallVelocity() ||
         !this.velocityModule.reduceSetting.value) {
         return
      }
      if (RotationManager.isSilentRotationMode()) {
         this.applyRandomizedPitch(event)
      } else {
         this.applyYawAndPitch(event)
      }
   }

   override fun onTick() {
      this.groundCollisionPresent = hasUsableGroundCollision()
      this.recentPositionCorrections.removeIf { receivedAt -> System.currentTimeMillis() > receivedAt + 500L }
      if (this.recentPositionCorrections.size > 2) {
         this.correctionCooldown.reset()
      }
   }

   companion object {
      private var sprintModule: SprintModule? = null
      private var fastWebModule: FastWebModule? = null

      @JvmStatic
      fun hasRaisedPlayerCollision(): Boolean {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         return hasPlayerCollisionAtHeight(player.getY() + 0.1)
      }

      private fun allOccupiedBlocksHaveCollision(): Boolean {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val positions: Set<BlockPos> = PlayerStateUtils.getSurroundPositions(player)
         if (positions.size != 4) {
            return false
         }
         for (position in positions) {
            if (level.getBlockState(position)
               .getCollisionShape(level, position).isEmpty()) {
               return false
            }
         }
         return true
      }

      @JvmStatic
      fun hasUsableGroundCollision(): Boolean {
         return !PlayerStateUtils.isFallFlying() && hasRaisedPlayerCollision()
      }

      @JvmStatic
      fun hasCollisionAtHeight(player: Player, height: Double): Boolean {
         var bounds: AABB = player.getBoundingBox()
         bounds = bounds.deflate(BoundingBoxUtils.blockCornerInset, 0.0, BoundingBoxUtils.blockCornerInset)
         bounds = bounds.setMinY(height)
         val collisionBounds: AABB = bounds.setMaxY(height + 0.1)
         return BlockPos.betweenClosedStream(collisionBounds).anyMatch { position -> intersectsCollision(collisionBounds, position) }
      }

      private fun intersectsCollision(bounds: AABB, position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val state: BlockStateBase = level.getBlockState(position)
         val shape: VoxelShape = state.getCollisionShape(level, position)
         return !shape.isEmpty() && bounds.intersects(shape.bounds().move(position))
      }

      @JvmStatic
      fun hasPlayerCollisionAtHeight(height: Double): Boolean {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         return hasCollisionAtHeight(player, height)
      }

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         sprintModule = modules.getModule(SprintModule::class.java) as SprintModule
         fastWebModule = modules.getModule(FastWebModule::class.java) as FastWebModule
      }
   }

}
