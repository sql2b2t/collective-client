package me.collective.client.modules.render

import me.collective.client.render.RenderUtils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

data class TracerEndpoints(
   val start: Vec3,
   val end: Vec3
) {

   companion object {

      internal fun standing(player: Player, position: Vec3): TracerEndpoints {
         val pose: Pose = player.getPose()
         val eyeHeight: Double = player.getEyeHeight(pose).toDouble()
         val eyePosition: Vec3 = position.add(0.0, eyeHeight, 0.0)
         return TracerEndpoints(position, eyePosition)
      }

      internal fun riding(player: Player, position: Vec3): TracerEndpoints {

         var position = position.add(0.0, 0.65, 0.0)
         val pose: Pose = player.getPose()
         val eyeHeight: Float = player.getEyeHeight(pose)
         val eyeOffset: Double = (eyeHeight - 0.65F).toDouble()
         val eyePosition: Vec3 = position.add(0.0, eyeOffset, 0.0)
         return TracerEndpoints(position, eyePosition)
      }

      internal fun sleeping(player: Player): TracerEndpoints {
         val bedDirection: Direction = player.getBedOrientation()!!
         val blockPosition: BlockPos = player.blockPosition()
         val headPosition: Vec3 = Vec3.atCenterOf(blockPosition).relative(bedDirection, 0.2).add(0.0, 0.1, 0.0)
         val oppositeDirection: Direction = bedDirection.getOpposite()

         val footPosition: Vec3 = headPosition.relative(oppositeDirection, 1.6)
         return TracerEndpoints(footPosition, headPosition)
      }

      internal fun lowPose(player: Player, position: Vec3, bounds: AABB): TracerEndpoints {
         val horizontalDiagonal: Double = Math.hypot(bounds.getXsize(), bounds.getZsize())
         val tickDelta: Float = RenderUtils.getPartialTick()
         val bodyYaw: Float = Mth.lerp(tickDelta, player.yBodyRotO, player.yBodyRot)
         val pitch: Float = if (player.isVisuallyCrawling())
            player.getSwimAmount(tickDelta)
         else
            player.getViewXRot(tickDelta)
         val pitchFraction: Float = pitch / 90.0F
         val viewDirection: Vec3 = player.calculateViewVector(pitch, bodyYaw)
         val horizontalDirection: Vec3 = player.calculateViewVector(0.0F, bodyYaw)

         var position = position
         if (player.isFallFlying()) {
            val remainingGlidePitch: Float = 45.0F - Math.abs(pitchFraction)
            val verticalOffset: Double = (-Math.abs(remainingGlidePitch * 0.01F * 0.5F)).toDouble()
            position = position
               .add(viewDirection)
               .add(horizontalDirection.scale(pitchFraction.toDouble()))
               .add(0.0, verticalOffset, 0.0)
         }

         val backwardDistance: Double = -horizontalDiagonal
         val centerPosition: Vec3 = position.add(viewDirection.scale(backwardDistance)).add(0.0, bounds.getYsize() * 0.5, 0.0)
         val pitchOffset: Vec3 = horizontalDirection.scale(0.3 * pitchFraction.toDouble())
         val downwardOffset: Double = -Math.abs(0.3 * pitchFraction.toDouble())
         val start: Vec3 = centerPosition.add(pitchOffset).add(0.0, downwardOffset, 0.0)
         val end: Vec3 = start.add(viewDirection.scale(horizontalDiagonal * 2.0))
         return TracerEndpoints(start, end)
      }

   }

}
