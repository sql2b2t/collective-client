package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.modules.exploit.ReachModule
import me.collective.client.render.RenderUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.world.WorldUtils

import java.util.function.BiFunction
import java.util.function.Function
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3

class RaycastUtils private constructor() : ClientAccess {
   companion object {
      // Лямбды статичны: raycast вызывается ~45 раз на расчёт exposure,
      // раньше каждая пара лямбд аллоцировалась на каждый вызов.
      private val CLIP_BLOCK = BiFunction<BlockRaycastContext, BlockPos, BlockHitResult?> { blockContext, blockPos ->
            val level = ClientAccess.collectiveMinecraft.level ?: return@BiFunction null
            var state = level.getBlockState(blockPos)
            val classifier = blockContext.classifier

            val override = classifier.test(blockPos, state)
            if (override == BlockRaycastOverride.IGNORE) {
               state = Blocks.AIR.defaultBlockState()
            } else if (override == BlockRaycastOverride.SOLID) {
               state = Blocks.BEDROCK.defaultBlockState()
            }

            val shape = blockContext.getBlockShape(state, level, blockPos)
            // Журнал блочных чтений для кэша урона взрыва (null у обычных
            // пользователей raycast — поведение не меняется).
            blockContext.reads?.record(blockPos, state, shape)
            if (shape.isEmpty) {
               // VoxelShape.clip для пустой формы возвращает null (VoxelShape.clip),
               // а clipWithInteractionOverride при null-клипе возвращает null:
               // пропуск строго эквивалентен полному вызову.
               return@BiFunction null
            }
            val blockStart = blockContext.start
            val blockEnd = blockContext.end
            level.clipWithInteractionOverride(blockStart, blockEnd, blockPos, shape, state)
         }
      private val ON_MISS = Function<BlockRaycastContext, BlockHitResult> { missContext ->
            val missStart = missContext.start
            val missEnd = missContext.end
            val difference = missStart.subtract(missEnd)
            val missLocation = missContext.start
            val directionZ = difference.z
            val directionY = difference.y
            val directionX = difference.x
            val direction = Direction.getApproximateNearest(directionX, directionY, directionZ)
            val containingPosition = missContext.end
            val missPos = BlockPos.containing(containingPosition)
            BlockHitResult.miss(missLocation, direction, missPos)
         }

      @JvmStatic
      fun raycast(context: BlockRaycastContext): BlockHitResult {
         val start = context.start
         val end = context.end
         return BlockGetter.traverseBlocks(start, end, context, CLIP_BLOCK, ON_MISS)
      }

      @JvmStatic
      fun raycastFromRotation(
         origin: Vec3, yaw: Float, pitch: Float, range: Float
      ): HitResult {
         val raycastPlayer = ClientAccess.collectiveMinecraft.player
            ?: return BlockHitResult.miss(origin, Direction.UP, BlockPos.containing(origin))
         val raycastLevel = ClientAccess.collectiveMinecraft.level
            ?: return BlockHitResult.miss(origin, Direction.UP, BlockPos.containing(origin))
         val scaledPitch = pitch * MathConstants.RADIANS_PER_DEGREE
         val scaledNegatedYaw = -yaw * MathConstants.RADIANS_PER_DEGREE
         val yawCosine = Mth.cos(scaledNegatedYaw.toDouble())
         val yawSine = Mth.sin(scaledNegatedYaw.toDouble())
         val pitchCosine = Mth.cos(scaledPitch.toDouble())
         val pitchSine = Mth.sin(scaledPitch.toDouble())
         val direction = Vec3(
            (yawSine * pitchCosine).toDouble(),
            (-pitchSine).toDouble(),
            (yawCosine * pitchCosine).toDouble()
         )
         val scaledDirectionX = direction.x * range
         val scaledDirectionY = direction.y * range
         val offsetZ = direction.z * range
         val end = origin.add(scaledDirectionX, scaledDirectionY, offsetZ)
         val clipContext = ClipContext(
            origin,
            end,
            ClipContext.Block.OUTLINE,
            Fluid.NONE,
            raycastPlayer
         )

         return raycastLevel.clip(clipContext)
      }

      @JvmStatic
      fun raycastWithEntityTrace(player: LocalPlayer): HitResult {
         ReachModule.setNoEntityTraceSuppressed(true)
         val partialTick = RenderUtils.getPartialTick()

         val cameraEntity = ClientAccess.collectiveMinecraft.cameraEntity

         val hitResult = player.raycastHitResult(partialTick, cameraEntity ?: player)
         ReachModule.setNoEntityTraceSuppressed(false)
         return hitResult
      }

      @JvmStatic
      fun getGroundDistance(): Int {
         val player = ClientAccess.collectiveMinecraft.player!!
         val clipContext = ClipContext(
            groundTraceOrigin(),
            Vec3(player.x, groundTraceMinimumY(), player.z),
            ClipContext.Block.COLLIDER,
            Fluid.NONE,
            player
         )
         val groundHit = ClientAccess.collectiveMinecraft.level!!.clip(clipContext)

         val playerY = player.y
         val verticalDifference = playerY - groundHit.location.y
         return Math.round(verticalDifference).toInt()
      }

      private fun groundTraceOrigin(): Vec3 {
         return ClientAccess.collectiveMinecraft.player!!.position()
      }

      private fun groundTraceMinimumY(): Double {
         return ClientAccess.collectiveMinecraft.level!!.minY.toDouble()
      }

      @JvmStatic
      fun getCollisionBlockYOffset(): Int {
         val player = ClientAccess.collectiveMinecraft.player!!
         val playerY = player.y
         val roundedBaseY = Math.round(playerY).toInt() + 1
         val clipContext = ClipContext(
            player.position(),
            Vec3(player.x, WorldUtils.getTopY().toDouble(), player.z),
            ClipContext.Block.COLLIDER,
            Fluid.NONE,
            player
         )
         val collisionHit = ClientAccess.collectiveMinecraft.level!!.clip(clipContext)
         val hitBlockPos = collisionHit.blockPos
         return hitBlockPos.y - roundedBaseY - 1
      }

      @JvmStatic
      fun hasVisibleEntitySample(entity: Entity): Boolean {
         val bounds = entity.boundingBox
         val eyePosition = ClientAccess.collectiveMinecraft.player!!.eyePosition
         var heightFraction = 0.0f
         while (true) {
            if (!(heightFraction <= 1.0f)) {
               return false
            }

            val sampleY = bounds.minY + (bounds.maxY - bounds.minY) * heightFraction
            val samplePoints = BoundingBoxUtils.getCornersByDistance(bounds, sampleY)
            for (pointIndex in samplePoints.indices) {
               val endPoint = samplePoints[pointIndex]
               if (isLineVisible(eyePosition, endPoint)) {
                  return true
               }
            }

            heightFraction += 0.5f
         }
      }

      @JvmStatic
      fun isLineVisible(startPoint: Vec3, endPoint: Vec3): Boolean {
         val clipContext = ClipContext(
            startPoint,
            endPoint,
            ClipContext.Block.COLLIDER,
            Fluid.NONE,
            ClientAccess.collectiveMinecraft.player!!
         )

         val clipResult = ClientAccess.collectiveMinecraft.level!!.clip(clipContext)
         return clipResult.type == Type.MISS
      }
   }
}
