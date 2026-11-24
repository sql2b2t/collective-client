package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.render.RenderUtils
import me.collective.client.events.EventStage

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.AABB

class OnGroundSpeedHandler(speedModule: SpeedModule) : SpeedModeHandler(speedModule) {
   init {
      this.movementStage = 2
   }

   override fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Post == event.stage || this.shouldResetGroundMovement()) {
         return
      }
      super.onSendPosition(event)
      if (this.movementStage == 3) {
         val offset = if (this.hasVerticalMotionCollision()) 0.2 else 0.4
         event.y = event.y + offset + MovementPhysics.getJumpBoost()
      }
   }

   override fun onMove(event: LocalPlayerMoveEvent) {
      if (this.shouldResetGroundMovement()) {
         this.speedModule.reset()
         MovementPhysics.applyHorizontalMotion(event, MovementPhysics.getBaseMoveSpeed(true))
         return
      }
      if (this.movementStage == 4) {
         this.speedModule.reset()
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!player.onGround() && this.movementStage != 3) {
         return
      }
      if ((!player.horizontalCollision && player.zza != 0.0f) || player.xxa != 0.0f) {
         if (this.movementStage == 2) {
            this.moveSpeed *= 2.149
            this.movementStage = 3
         } else if (this.movementStage == 3) {
            this.movementStage = 2
            this.moveSpeed = this.lastHorizontalDistance
               - 0.66 * (this.lastHorizontalDistance - MovementPhysics.getBaseMoveSpeed(true))
         }
      }
      this.moveSpeed = Math.min(this.moveSpeed, this.speedModule.speedSetting.value.toDouble())
      this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.getBaseMoveSpeed(true))
      val motion = MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, this.moveSpeed)
      if (!ClientAccess.collectiveMinecraft.level!!.noCollision(player.boundingBox.expandTowards(motion[0], 0.0, motion[1]))) {
         this.speedModule.reset()
         this.moveSpeed = MovementPhysics.getBaseMoveSpeed(true)
      }
      MovementPhysics.applyHorizontalMotion(event, this.moveSpeed)
   }

   private fun shouldResetGroundMovement(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val bounds: AABB = player.boundingBox
      val deltaX = player.x - player.xo
      val deltaY = player.y - player.yo
      val deltaZ = player.z - player.zo
      if (!level.noCollision(bounds.move(deltaX, deltaY, deltaZ)) || level.noCollision(bounds.expandTowards(0.0, -1.0, 0.0))) {
         return true
      }
      if (!player.horizontalCollision && !(player.y > Math.floor(player.y)) &&
         ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)
      ) {
         val predictedY = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, 3).minY
         if (!(predictedY < player.y)) {
            return false
         }
      }
      return true
   }
}
