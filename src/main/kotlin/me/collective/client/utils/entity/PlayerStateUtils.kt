package me.collective.client.utils.entity

import me.collective.client.managers.ServerStateManager
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.world.BlockInteractionUtils

import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.settings.options.BlockerTargetOffsets

import java.util.HashSet
import java.util.stream.Stream
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.Vec3i
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodData
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemCooldowns
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class PlayerStateUtils private constructor() : ClientAccess {
   companion object {
      private val BODY_SAMPLE_HEIGHTS: FloatArray = floatArrayOf(1.62F, 1.27F, 0.4F)
      private val ENDER_PEARL_STACK: ItemStack = ItemStack(Items.ENDER_PEARL)

      @JvmStatic
      fun getMovementDirection(): Direction {
         val inputSnapshot: ClientInput = ClientAccess.collectiveMinecraft.player!!.input
         val yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()

         val motion = MovementPhysics.getHorizontalMotion(yaw, inputSnapshot, 1.0)
         val motionZ = motion[1]
         val motionX = motion[0]
         val angleRadians = Math.atan2(motionZ, motionX)
         val directionYaw = Math.toDegrees(angleRadians) - MathConstants.NINETY_DEGREES
         return Direction.fromYRot(directionYaw)
      }

      @JvmStatic
      fun getMovementVector(): Vec3 {
         val inputSnapshot: ClientInput = ClientAccess.collectiveMinecraft.player!!.input
         val yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
         val motion = MovementPhysics.getHorizontalMotion(yaw, inputSnapshot, 1.0)
         return Vec3(motion[0], 0.0, motion[1])
      }

      @JvmStatic
      fun isLocalHeightBelowOne(): Boolean {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         val bounds: AABB = player.getBoundingBox()
         val height = bounds.getYsize()

         return height < 1.0
      }

      @JvmStatic
      fun getBodySampleHeights(): FloatArray {
         return BODY_SAMPLE_HEIGHTS
      }

      @JvmStatic
      fun isHungerLimited(): Boolean {
         val creativePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         if (creativePlayer.isCreative()) {
            return false
         }
         val spectatorPlayer: LocalPlayer = creativePlayer
         if (spectatorPlayer.isSpectator()) {
            return false
         }
         val foodData: FoodData = creativePlayer.getFoodData()
         val foodLevel = foodData.getFoodLevel()

         return foodLevel <= 6
      }

      @JvmStatic
      fun streamBodySamplePositions(): Stream<Vec3> {
         val scalePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return Stream.empty()
         val positions: Stream.Builder<Vec3> = Stream.builder()

         for (height in BODY_SAMPLE_HEIGHTS) {
            val scaleAttribute: Holder<Attribute> = Attributes.SCALE
            val scale = scalePlayer.getAttributeValue(scaleAttribute)
            val positionSnapshot: Vec3 = scalePlayer.position()
            val scaledHeight = height * scale
            positions.add(positionSnapshot.add(0.0, scaledHeight, 0.0))
         }

         return positions.build()
      }

      @JvmStatic
      fun getLocalRoundedBlockPosition(): BlockPos {
         val playerSnapshot: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return BlockPos.ZERO

         return getRoundedBlockPosition(playerSnapshot)
      }

      @JvmStatic
      fun getTrackedFallDistance(): Float {
         val trackerSnapshot = ClientContext.playerStateTracker

         return trackerSnapshot.getFallDistance()
      }

      @JvmStatic
      fun hasArmor(entity: LivingEntity): Boolean {
         if (entity is Player) {
            for (stack in EquipmentUtils.getArmorStacks(entity)) {
               if (!stack.isEmpty) {
                  return true
               }
            }
         }

         return false
      }

      @JvmStatic
      fun isWearingElytra(player: Player): Boolean {
         val inventorySnapshot: Inventory = player.getInventory()
         val chestSlotSnapshot = net.minecraft.world.entity.EquipmentSlot.CHEST

         val inventoryIndex = chestSlotSnapshot.getIndex(36)
         val chestStackSnapshot: ItemStack = inventorySnapshot.getItem(inventoryIndex)
         val elytra = Items.ELYTRA
         return chestStackSnapshot.`is`(elytra)
      }

      @JvmStatic
      fun getRoundedBlockPosition(entity: LivingEntity): BlockPos {
         val xSnapshot = entity.getX()
         val y = entity.getY()
         val roundedY = Math.round(y).toDouble()
         val z = entity.getZ()
         return BlockPos.containing(xSnapshot, roundedY, z)
      }

      @JvmStatic
      fun isInCobweb(player: Player): Boolean {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
         val bounds: AABB = player.getBoundingBox()
         return level.getBlockStates(bounds).anyMatch { state ->
            val cobweb = Blocks.COBWEB
            state.`is`(cobweb)
         }
      }

      @JvmStatic
      fun isLocalWearingElytra(): Boolean {
         val playerSnapshot: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         return isWearingElytra(playerSnapshot)
      }

      @JvmStatic
      fun isFallFlying(): Boolean {
         val hookWasActive = ElytraFlyModule.hook
         if (!hookWasActive) {
            ElytraFlyModule.hook = true
         }
         val player = ClientAccess.collectiveMinecraft.player
         if (player == null) {
            if (!hookWasActive) {
               ElytraFlyModule.hook = false
            }
            return false
         }
         val fallFlying: Boolean = player.isFallFlying()
         if (!hookWasActive) {
            ElytraFlyModule.hook = false
         }
         return fallFlying
      }

      @JvmStatic
      fun isLocalLowProfile(): Boolean {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         return isLowProfile(player)
      }

      @JvmStatic
      fun isEnderPearlOnCooldown(): Boolean {
         val counterSnapshot: ServerStateManager = ClientContext.serverStateManager

         val partialTickCount = counterSnapshot.getLatencyTicks()
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         val cooldownsSnapshot: ItemCooldowns = player.getCooldowns()
         val partialTick = partialTickCount.toFloat()
         val pearlStack = ENDER_PEARL_STACK
         return cooldownsSnapshot.getCooldownPercent(pearlStack, partialTick) > 0.0
      }

      @JvmStatic
      fun getOffsetBlockPositions(entity: LivingEntity, offsets: List<out Vec3i>): Set<BlockPos> {
         val result = HashSet<BlockPos>()
         val occupiedPositions = getOccupiedBlockPositions(entity)
         for (position in occupiedPositions) {
            for (offset in offsets) {
               val candidate = position.offset(offset)
               if (!occupiedPositions.contains(candidate)) {
                  result.add(candidate)
               }
            }
         }

         return result
      }

      @JvmStatic
      fun getSurroundPositions(entity: LivingEntity): Set<BlockPos> {
         val surroundOffsets = BlockerTargetOffsets.SURROUND
         val offsets: List<Vec3i> = surroundOffsets.offsets
         return getOffsetBlockPositions(entity, offsets)
      }

      @JvmStatic
      fun isInWater(player: Player): Boolean {
         val heightOffset: Float
         if (player.onGround()) {
            heightOffset = 0.0F
         } else {

            heightOffset = 0.08F
         }

         val xSnapshot = player.getX()
         val ySnapshot = player.getBoundingBox().minY - heightOffset
         val z = player.getZ()
         val blockPositionSnapshot = BlockPos.containing(xSnapshot, ySnapshot, z)

         return BlockInteractionUtils.getBlock(blockPositionSnapshot) === Blocks.WATER || player.isInWater()
      }

      @JvmStatic
      fun isInLiquid(player: Player): Boolean {

         return isInWater(player) || player.isInLava()
      }

      @JvmStatic
      fun getOccupiedBlockPositions(entity: LivingEntity): Set<BlockPos> {

         val roundedPosition = getRoundedBlockPosition(entity)
         val positions = HashSet<BlockPos>()

         var bounds: AABB = BoundingBoxUtils.getInterpolationBounds(entity)
         val shrinkAmount = -BoundingBoxUtils.blockCornerInset
         bounds = bounds.inflate(shrinkAmount)
         var xSnapshot = bounds.minX
         var ySnapshot = roundedPosition.getY().toDouble()
         val minZ = bounds.minZ
         positions.add(BlockPos.containing(xSnapshot, ySnapshot, minZ))
         xSnapshot = bounds.minX
         ySnapshot = roundedPosition.getY().toDouble()
         val maxZ = bounds.maxZ
         positions.add(BlockPos.containing(xSnapshot, ySnapshot, maxZ))
         xSnapshot = bounds.maxX
         ySnapshot = roundedPosition.getY().toDouble()
         val secondMinZ = bounds.minZ
         positions.add(BlockPos.containing(xSnapshot, ySnapshot, secondMinZ))
         xSnapshot = bounds.maxX
         ySnapshot = roundedPosition.getY().toDouble()
         val secondMaxZ = bounds.maxZ
         positions.add(BlockPos.containing(xSnapshot, ySnapshot, secondMaxZ))
         return positions
      }

      @JvmStatic
      fun isLowProfile(player: Player): Boolean {
         val bounds: AABB = player.getBoundingBox()
         val height = bounds.getYsize()

         return height <= 1.0
      }

      @JvmStatic
      fun oppositeHand(hand: InteractionHand): InteractionHand {
         return if (hand === InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
      }
   }
}
