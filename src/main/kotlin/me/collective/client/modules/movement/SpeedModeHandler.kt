package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.events.EventStage

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.AABB

abstract class SpeedModeHandler(
   @JvmField protected val speedModule: SpeedModule
) : ClientAccess {
   @JvmField
   var moveSpeed: Double = 0.0

   @JvmField
   var lastHorizontalDistance: Double = 0.0

   @JvmField
   var movementStage: Int = 0

   open fun onRotationUpdate() {
   }

   fun hasVerticalMotionCollision(): Boolean {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val bounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val verticalMotion = ClientAccess.collectiveMinecraft.player!!.deltaMovement.y
      return !level.noCollision(bounds.move(0.0, verticalMotion, 0.0))
   }

   open fun onEnable() {
   }

   open fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Post == event.stage) {
         return
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         movementStage = 4
         moveSpeed = 0.0
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val deltaX = player.x - player.xo
      val deltaZ = player.z - player.zo
      lastHorizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
   }

   abstract fun onMove(event: LocalPlayerMoveEvent)
}
