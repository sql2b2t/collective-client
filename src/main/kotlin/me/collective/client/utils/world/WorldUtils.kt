package me.collective.client.utils.world

import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.EnchantmentUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.settings.options.DimensionType
import me.collective.client.settings.options.BlockSampleMode

import com.google.common.collect.AbstractIterator
import java.util.ArrayList
import java.util.Spliterator
import java.util.Spliterators
import java.util.stream.Stream
import java.util.stream.StreamSupport
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.Vec3i
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.VehicleEntity
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class WorldUtils private constructor() : ClientAccess {
   @JvmRecord
   data class EntityRange(val distance: Double, val origin: Vec3?)

   private class SphereBlockIterator(center: Vec3, private val radius: Float) : AbstractIterator<BlockPos>() {
      init {
         require(radius in 0f..128f) { "radius must be in 0..128, got $radius" }
      }
      private val center: BlockPos = BlockPos.containing(center)
      private val extent: Int = Math.ceil(radius.toDouble()).toInt()
      private var offsetX: Int = -extent
      private var offsetY: Int = -extent
      private var offsetZ: Int = -extent

      override fun computeNext(): BlockPos? {
         while (offsetX <= extent) {
            while (offsetY <= extent) {
               while (offsetZ <= extent) {
                  val z = offsetZ
                  val y = offsetY
                  val x = offsetX
                  val position = center.offset(x, y, z)
                  offsetZ++
                  val radiusAsDouble = radius.toDouble()
                  if (center.closerThan(position, radiusAsDouble)) {
                     return position
                  }
               }

               offsetZ = -extent
               offsetY++
            }

            offsetY = -extent
            offsetX++
         }

         return endOfData()
      }

   }

   companion object {
      private val autoCrystal: AutoCrystalModule? get() = ClientContext.getModule(AutoCrystalModule::class.java)

      @JvmField
      val BASTION_FLOOR_BLOCKS: List<Block> = listOf(
         Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE
      )

      @JvmField
      val ANCIENT_CITY_FLOOR_BLOCKS: List<Block> = listOf(
         Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
         Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE, Blocks.CHISELED_DEEPSLATE, Blocks.SCULK
      )

      @JvmField
      val TRIAL_CHAMBER_FLOOR_BLOCKS: List<Block> = listOf(
         Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.WAXED_COPPER_BLOCK,
         Blocks.WAXED_OXIDIZED_COPPER_GRATE, Blocks.CHISELED_TUFF_BRICKS
      )

      @JvmStatic
      fun getTopY(): Float {
         val level = ClientAccess.collectiveMinecraft.level ?: return 0.0f
         val minimumY = level.minY
         return (minimumY + level.height).toFloat()
      }

      @JvmStatic
      fun getLoadedChunks(): List<LevelChunk> {
         val chunks = ArrayList<LevelChunk>()
         val level = ClientAccess.collectiveMinecraft.level ?: return chunks
         val player = ClientAccess.collectiveMinecraft.player ?: return chunks
         val distance = ClientAccess.collectiveMinecraft.options.renderDistance().get()
         for (x in -distance..distance) {
            for (z in -distance..distance) {
               val chunk = level.chunkSource.getChunkNow(
                  player.x.toInt() / 16 + x,
                  player.z.toInt() / 16 + z
               )
               if (chunk != null) chunks.add(chunk)
            }
         }
         return chunks
      }

      @JvmStatic
      fun getDimension(): DimensionType {
         val level = ClientAccess.collectiveMinecraft.level ?: return DimensionType.OVERWORLD
         val dimensionId: Identifier = level.dimension().identifier()
         return when (dimensionId.path) {
            "the_nether" -> DimensionType.THE_NETHER
            "the_end" -> DimensionType.THE_END
            else -> DimensionType.OVERWORLD
         }
      }

      @JvmStatic
      fun getLoadedBlockEntities(): List<BlockEntity> {
         val blockEntities = ArrayList<BlockEntity>()
         val chunks: Iterator<LevelChunk> = getLoadedChunks().iterator()
         while (chunks.hasNext()) {
            val chunk = chunks.next()
            if (!chunk.blockEntities.isEmpty()) {
               try {
                  blockEntities.addAll(ArrayList(chunk.blockEntities.values))
               } catch (failure: Throwable) {
                  autoCrystal?.completeCalculation()
                  break
               }
            }
         }
         return blockEntities
      }

      @JvmStatic
      fun hasFluid(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val fluidState: FluidState = level.getFluidState(position)
         return !fluidState.isEmpty
      }

      @JvmStatic
      fun hasCollision(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val collisionState = level.getBlockState(position)
         val collisionShape: VoxelShape = collisionState.getCollisionShape(level, position)
         return !collisionShape.isEmpty
      }

      @JvmStatic
      fun isReplaceable(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         return level.getBlockState(position).canBeReplaced()
      }

      @JvmStatic
      fun getHardness(position: BlockPos): Float {
         val level = ClientAccess.collectiveMinecraft.level ?: return 0.0f
         val hardnessState = level.getBlockState(position)
         return hardnessState.getDestroySpeed(level, position)
      }

      @JvmStatic
      fun canSeeFromEyes(target: Vec3): Boolean {
         val player = ClientAccess.collectiveMinecraft.player ?: return false
         val eyePosition = player.getEyePosition()
         return canSee(eyePosition, target)
      }

      @JvmStatic
      fun reloadRenderer() {
         if (ClientAccess.collectiveMinecraft.level != null) {
            ClientAccess.collectiveMinecraft.levelRenderer.allChanged()
         }
      }

      @JvmStatic
      fun isPlacementOccupied(position: BlockPos): Boolean {
         return ClientContext.worldStateManager.isPlacementOccupied(position)
      }

      @JvmStatic
      fun getBlock(position: BlockPos): Block {
         val level = ClientAccess.collectiveMinecraft.level!!
         return level.getBlockState(position).block
      }

      @JvmStatic
      fun getBreakProgress(
         stack: ItemStack, state: BlockState, onGround: Boolean
      ): Double {
         val hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
         if (hardness == -1.0f) {
            return 0.0
         }
         val damagePerHardness = getMiningSpeed(stack, state, onGround) / hardness
         if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
            return damagePerHardness / 100.0
         }
         return damagePerHardness / 30.0
      }

      @JvmStatic
      fun isInteractive(hit: HitResult?): Boolean {
         if (hit == null) {
            return false
         }
         if (hit is BlockHitResult) {
            val position = hit.blockPos
            val level = ClientAccess.collectiveMinecraft.level ?: return false
            val state = level.getBlockState(position)
            if (state.hasBlockEntity()) {
               return true
            }
            val block = state.block
            if (BlockInteractionUtils.isInteractiveBlock(block)) {
               return true
            }
         }
         if (hit !is EntityHitResult) {
            return false
         }
         val entity = hit.entity
         return entity is VehicleEntity || entity is Villager
      }

      @JvmStatic
      fun canSeeAny(origin: Vec3, points: Iterable<*>): Boolean {
         val pointsIterator = points.iterator()
         while (pointsIterator.hasNext()) {
            val point = pointsIterator.next() as Vec3
            if (canSee(origin, point)) {
               return true
            }
         }
         return false
      }

      @JvmStatic
      fun distanceToBounds(point: Vec3, bounds: AABB): Double {
         val nearestPoint = BoundingBoxUtils.clampToBounds(point, bounds)
         return point.distanceTo(nearestPoint)
      }

      @JvmStatic
      fun isStructureChest(
         chunk: ChunkAccess, position: BlockPos, fortress: Boolean, bastion: Boolean, ancientCity: Boolean, dungeon: Boolean, trialChambers: Boolean
      ): Boolean {
         val chestState = chunk.getBlockState(position)
         val chestBlock = Blocks.CHEST
         if (!chestState.`is`(chestBlock)) {
            return false
         }
         val belowPosition = position.below()
         val floorState = chunk.getBlockState(belowPosition)
         val chestTypeProperty: EnumProperty<ChestType> = ChestBlock.TYPE
         val singleChest = chestState.getValue(chestTypeProperty) == ChestType.SINGLE
         if (getDimension() == DimensionType.THE_NETHER) {
               if (fortress && singleChest) {
                  val fortressFloorBlock = Blocks.NETHER_BRICKS
                  if (floorState.`is`(fortressFloorBlock)) {
                  return true
               }
            }
            if (bastion) {
               if (BASTION_FLOOR_BLOCKS.contains(floorState.block)) {
                  return true
               }
            }
            return false
         }
         if (getDimension() == DimensionType.OVERWORLD) {
            if (!singleChest) {
               return false
            }
            if (trialChambers && isTrialChamberChest(chunk, position, floorState)) {
               return true
            }
            if (ancientCity && isAncientCityChest(position, floorState)) {
               return true
            }
            if (dungeon) {
               val mossyFloorBlock = Blocks.MOSSY_COBBLESTONE
               if (floorState.`is`(mossyFloorBlock)) {
                  return true
               }
               val cobbleFloorBlock = Blocks.COBBLESTONE
               if (floorState.`is`(cobbleFloorBlock)) {
                  return true
               }
            }
            return false
         }
         return false
      }

      private fun isTrialChamberChest(chunk: ChunkAccess, position: BlockPos, floorState: BlockState): Boolean {
         if (position.y > 34) {
            return false
         }
         if (!TRIAL_CHAMBER_FLOOR_BLOCKS.contains(floorState.block)) {
            return false
         }
         val polishedTuffAbovePosition = position.above(3)
         val polishedTuffState = chunk.getBlockState(polishedTuffAbovePosition)
         val polishedTuffBlock = Blocks.POLISHED_TUFF
         val polishedTuffAbove = polishedTuffState.`is`(polishedTuffBlock)
         var clearAbove = true
         var heightOffset = 1
         while (true) {
            if (heightOffset > 2) {
               return clearAbove && polishedTuffAbove
            }
            val abovePosition = position.above(heightOffset)
            val clearanceState = chunk.getBlockState(abovePosition)
            if (clearanceState.block !is AirBlock) {
               clearAbove = false
            }
            heightOffset++
         }
      }

      private fun isAncientCityChest(position: BlockPos, floorState: BlockState): Boolean {
         if (position.y > -32) {
            return false
         }
         if (position.y < -51) {
            return false
         }
         return ANCIENT_CITY_FLOOR_BLOCKS.contains(floorState.block)
      }

      @JvmStatic
      fun canSee(
         origin: Vec3, target: Vec3, targetBlock: BlockPos?, targetFace: Direction?
      ): Boolean {
         if (target.distanceTo(origin) > 128.0) {
            return false
         }
         val context = BlockRaycastContext.Builder(origin, target).build()
         val hit = RaycastUtils.raycast(context)
         if (hit.type == HitResult.Type.MISS) {
            return true
         }
         if (hit.location == target) {
            return true
         }
         if (hit.blockPos != targetBlock) {
            return false
         }
         return hit.direction == targetFace || targetFace == null
      }

      @JvmStatic
      fun canSee(origin: Vec3, target: Vec3): Boolean {
         val capturedTargetBlock = BlockPos.containing(target)
         return canSee(origin, target, capturedTargetBlock, null)
      }

      @JvmStatic
      fun canSeeBlock(position: Vec3i): Boolean {
         val capturedCenter = Vec3.atCenterOf(position)
         return canSeeFromEyes(capturedCenter)
      }

      @JvmStatic
      fun canSeeAny(points: Iterable<*>): Boolean {
         val player = ClientAccess.collectiveMinecraft.player ?: return false
         val eyePosition = player.getEyePosition()
         return canSeeAny(eyePosition, points)
      }

      @JvmStatic
      fun findVisiblePoint(
         position: BlockPos, samplingMode: BlockSampleMode
      ): Vec3? {
         val points = samplingMode.sampleBlock(position).iterator()
         while (points.hasNext()) {
            val point = points.next()
            if (canSeeFromEyes(point)) {
               return point
            }
         }
         return null
      }

      @JvmStatic
      fun streamSphereBlocks(center: Vec3, radius: Float): Stream<BlockPos> {
         val positions: Iterator<BlockPos> = SphereBlockIterator(center, radius)
         val spliterator: Spliterator<BlockPos> = Spliterators.spliteratorUnknownSize(positions, 0)
         return StreamSupport.stream(spliterator, false)
      }

      @JvmStatic
      fun collectSphereBlocks(
         center: Vec3, radius: Float, excludeAir: Boolean
      ): List<BlockPos> {
         return streamSphereBlocks(center, radius).filter { position -> !excludeAir || !isAir(position) }.toList()
      }

      @JvmStatic
      fun isTargetable(entity: Entity): Boolean {
         if (entity === ClientAccess.collectiveMinecraft.player!!) {
            return false
         }
         if (entity is Player) {
            if (ClientContext.socialRegistry.isFriendPlayer(entity)) {
               return false
            }
         }
         return entity.isAlive()
      }

      private fun getMiningSpeed(
         stack: ItemStack, state: BlockState, onGround: Boolean
      ): Double {
         var speed = stack.getDestroySpeed(state).toDouble()
         if (speed > 1.0) {
            val efficiencyEnchantment = Enchantments.EFFICIENCY
            val efficiency = EnchantmentUtils.getLevel(efficiencyEnchantment, stack)
            if (efficiency > 0 && !stack.isEmpty) {
               speed += (efficiency * efficiency + 1).toDouble()
            }
         }
         val player = ClientAccess.collectiveMinecraft.player ?: return 0.0
         if (MobEffectUtil.hasDigSpeed(player)) {
            val hasteBase = 1.0f
            val hasteAmplifier = MobEffectUtil.getDigSpeedAmplification(player) + 1
            speed *= (hasteBase + hasteAmplifier * 0.2f).toDouble()
         }
         val miningFatigue: Holder<net.minecraft.world.effect.MobEffect> = MobEffects.MINING_FATIGUE
         if (player.hasEffect(miningFatigue)) {
            val fatigueEffect: Holder<net.minecraft.world.effect.MobEffect> = MobEffects.MINING_FATIGUE
            val fatigue: MobEffectInstance = player.getEffect(fatigueEffect)!!
            val selectedFatigueFactor = when (fatigue.amplifier) {
               0 -> 0.3f
               1 -> 0.09f
               2 -> 0.0027f
               else -> 0.00081f
            }
            speed *= selectedFatigueFactor.toDouble()
         }
         val waterTag: TagKey<net.minecraft.world.level.material.Fluid> = FluidTags.WATER
         if (player.isEyeInFluid(waterTag)) {
            val aquaAffinity = Enchantments.AQUA_AFFINITY
            val headSlot = EquipmentSlot.HEAD
            if (!EnchantmentUtils.hasEnchantment(aquaAffinity, headSlot)) {
               speed /= 5.0
            }
         }
         if (!onGround) {
            speed /= 5.0
         }
         return speed
      }

      @JvmStatic
      fun getEntityDistance(entity: Entity, sampleBody: Boolean): Double {
         val range: EntityRange = getEntityRange(
            entity, sampleBody
         )
         return range.distance
      }

      @JvmStatic
      fun isBedrockOrObsidian(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         if (level.getBlockState(position).block !== Blocks.BEDROCK) {
            if (level.getBlockState(position).block !== Blocks.OBSIDIAN) {
               return false
            }
         }
         return true
      }

      @JvmStatic
      fun isAir(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         return level.getBlockState(position).isAir
      }

      @JvmStatic
      fun getEntityRange(
         entity: Entity, sampleBody: Boolean
      ): EntityRange {
         val player = ClientAccess.collectiveMinecraft.player ?: return EntityRange(Double.MAX_VALUE, null)
         val selectedHeights: FloatArray = if (sampleBody) {
            PlayerStateUtils.getBodySampleHeights()
         } else {
            val singleHeight = FloatArray(1)
            singleHeight[0] = player.getEyeHeight()
            singleHeight
         }
         val sampleHeights = selectedHeights
         val selectedScale: Float = if (sampleBody) {
            val scaleAttribute = Attributes.SCALE
            player.getAttributeValue(scaleAttribute).toFloat()
         } else {
            1.0f
         }
         val basePosition = BoundingBoxUtils.getInterpolationBounds(player).bottomCenter
         val entityBounds = entity.boundingBox
         var minimumDistance = Double.MAX_VALUE
         var closestOrigin: Vec3? = null
         for (sampleHeight in sampleHeights) {
            val scaledSampleHeight = sampleHeight * selectedScale
            val origin = basePosition.add(0.0, scaledSampleHeight.toDouble(), 0.0)
            if (entity is EnderDragon) {
               for (part in entity.subEntities) {
                  val partBounds = part.boundingBox
                  val partDistance = distanceToBounds(origin, partBounds)
                  if (partDistance < minimumDistance) {
                     minimumDistance = partDistance
                     closestOrigin = origin
                  }
               }
            } else {
               val boundsDistance = distanceToBounds(origin, entityBounds)
               if (boundsDistance < minimumDistance) {
                  minimumDistance = boundsDistance
                  closestOrigin = origin
               }
            }
         }
         return EntityRange(minimumDistance, closestOrigin)
      }

      @JvmStatic
      fun canMineBlock(position: BlockPos?): Boolean {
         if (position == null) {
            return false
         }
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val state = level.getBlockState(position)
         val player = ClientAccess.collectiveMinecraft.player ?: return false
         if (!player.isCreative) {
            if (state.getDestroySpeed(level, position) < 0.0f) {
               return false
            }
         }
         if (state.isAir) {
            return false
         }
         val waterBlock = Blocks.WATER
         if (!state.`is`(waterBlock)) {
            val lavaBlock = Blocks.LAVA
            if (!state.`is`(lavaBlock)) {
               return state.getShape(level, position) !== Shapes.empty()
            }
         }
         return false
      }
   }
}
