package me.collective.client.modules.misc


import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.managers.RotationManager
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting

import me.collective.mixin.PositionMoveRotationAccessor
import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

class NoRotateModule : Module("NoRotate", "Cancels the rotations server sets for you.", ModuleCategory.MISC) {
   private val inBlocksSetting: Setting<InBlocksChoice>
   private var spoofYaw: Float = 0.0f
   private var spoofPitch: Float = 0.0f
   private var spoofPending: Boolean = false

   private fun isInsideBlockingShape(): Boolean {
      val ladderPlayer = ClientAccess.collectiveMinecraft.player!!
      if (ladderPlayer!!.isSuppressingSlidingDownLadder) {
         return false
      }
      val cobwebPlayer = ClientAccess.collectiveMinecraft.player!!
      if (PlayerStateUtils.isInCobweb(cobwebPlayer!!)) {
         return false
      }

      val level: ClientLevel? = ClientAccess.collectiveMinecraft.level!!
      val blockPlayer = ClientAccess.collectiveMinecraft.player!!
      val blockPosition = blockPlayer!!.blockPosition()
      val blockState: BlockState = level!!.getBlockState(blockPosition)
      if (blockState.block is FallingBlock) {
         return false
      }

      val collisionLevel = ClientAccess.collectiveMinecraft.level!!
      val collisionPosition = BlockPos.ZERO
      val collisionShape: VoxelShape = blockState.getCollisionShape(collisionLevel!!, collisionPosition)
      if (collisionShape.isEmpty) {
         return false
      }
      val heightPlayer = ClientAccess.collectiveMinecraft.player!!
      val playerY = heightPlayer!!.y
      val floorPlayer = ClientAccess.collectiveMinecraft.player!!
      val floorY = floorPlayer!!.y
      val heightWithinBlock = playerY - Math.floor(floorY)
      if (heightWithinBlock >= collisionShape.bounds().maxY) {
         return false
      }
      val localShapeBounds = collisionShape.bounds()
      val positionPlayer = ClientAccess.collectiveMinecraft.player!!
      val shapePosition = positionPlayer!!.blockPosition()
      val worldShapeBounds = localShapeBounds.move(shapePosition)
      val boundsPlayer = ClientAccess.collectiveMinecraft.player!!
      val playerBounds = boundsPlayer!!.boundingBox
      val inflatedPlayerBounds = playerBounds.inflate(-0.05)
      return worldShapeBounds.intersects(inflatedPlayerBounds)
   }

   @EventHandler
   private fun onPacketSend(
      event: PacketEvent.Send
   ) {
      if (event.packet is ServerboundMovePlayerPacket &&
         this.spoofPending) {
         val movementPacket = event.packet as ServerboundMovePlayerPacket
         val packetAccessor = movementPacket as ServerboundMovePlayerPacketAccessor
         packetAccessor.setYaw(this.spoofYaw)
         packetAccessor.setPitch(this.spoofPitch)
         this.spoofPending = false
      }

      val inBlocksSettingSnapshot = this.inBlocksSetting
      if (inBlocksSettingSnapshot.value == InBlocksChoice.CANCEL &&
         !MovementPhysics.isLocalPlayerMoving() &&
         event.packet is ServerboundMovePlayerPacket &&
         this.isInsideBlockingShape()) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet !is ClientboundPlayerPositionPacket) {
         return
      }
      val positionPacket = event.packet as ClientboundPlayerPositionPacket

      if (this.isWorldUnavailable()) {
         return
      }
      val playerTicks = ClientAccess.collectiveMinecraft.player!!.tickCount

      if (playerTicks <= (20)) {
         return
      }
      val minecraft: Minecraft = ClientAccess.collectiveMinecraft
      if (minecraft.isLocalServer) {
         return
      }

      val positionPlayer = ClientAccess.collectiveMinecraft.player!!
      val playerPosition = PositionMoveRotation.of(positionPlayer!!)
      val serverChange = positionPacket.change()
      val relativeFlags = positionPacket.relatives()
      val absolutePosition = PositionMoveRotation.calculateAbsolute(playerPosition, serverChange, relativeFlags)
      val yawPlayer = ClientAccess.collectiveMinecraft.player!!
      var preservedYaw = yawPlayer!!.yRot
      val pitchPlayer = ClientAccess.collectiveMinecraft.player!!
      var preservedPitch = pitchPlayer!!.xRot
      if (positionPacket.relatives().contains(Relative.Y_ROT)) {
         preservedYaw = 0.0f
      }

      if (positionPacket.relatives().contains(Relative.X_ROT)) {
         preservedPitch = 0.0f
      }

      (positionPacket.change() as PositionMoveRotationAccessor).`collective$setYaw`(preservedYaw)
      (positionPacket.change() as PositionMoveRotationAccessor).`collective$setPitch`(preservedPitch)
      val inBlocksSettingSnapshot = this.inBlocksSetting
      if (inBlocksSettingSnapshot.value == InBlocksChoice.SPOOF ||
         RotationManager.isMovementSyncEnabled()) {
         this.spoofYaw = absolutePosition.yRot()
         this.spoofPitch = absolutePosition.xRot()
         this.spoofPending = true
      }
   }

   init {
      val inBlocksDefault = InBlocksChoice.OFF
      inBlocksSetting = add(EnumSetting("InBlocks", inBlocksDefault))
   }

   internal enum class InBlocksChoice(private val displayName: String) : NamedEntry {
      OFF("Off"),
      CANCEL("Cancel"),
      SPOOF("Spoof");

      override fun getName(): String {
         return this.displayName
      }
   }
}
