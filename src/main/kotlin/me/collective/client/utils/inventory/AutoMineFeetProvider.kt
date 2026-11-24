package me.collective.client.utils.inventory

import me.collective.client.managers.RotationManager
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.combat.AutoMineMiningTracker
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.Setting
import me.collective.client.settings.options.SpeedMineRebreakChoice

import java.util.ArrayList
import java.util.function.Consumer
import java.util.stream.Stream
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Plane
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class AutoMineFeetProvider(module: AutoMineModule) : AutoMineProviderBase(module) {
   private val protectedPositions: MutableSet<BlockPos> = HashSet()

   private fun canPlaceCrystal(position: BlockPos): Boolean {
      val requireUpperAir = autoCrystal.requireUpperAirSetting.value
      val lowerHitBoxSetting: Setting<Boolean> = autoCrystal.lowerHitBoxSetting
      val lowerHitBox = lowerHitBoxSetting.value
      val checkRotation = !RotationManager.isMovementSyncEnabled()
      return BlockInteractionUtils.canPlaceCrystal(position, requireUpperAir, true, false, false, lowerHitBox, checkRotation)
   }

   private fun isProtectedSelfPosition(position: BlockPos): Boolean {
      val safeSetting: Setting<Boolean> = this.autoMine.safeSetting
      if (safeSetting.value) {
         val protectedIterator = this.protectedPositions.iterator()

         while (protectedIterator.hasNext()) {
            if (protectedIterator.next() == position) {
               return true
            }
         }
      }

      val selfPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      val boundsSnapshot = selfPlayer.boundingBox
      val playerY = selfPlayer.y
      val insetBounds = boundsSnapshot.setMaxY(playerY).deflate(0.0, -1.0, 0.0)

      val occupiedPositions: List<BlockPos> = BlockPos.betweenClosedStream(insetBounds)
         .map { occupiedPosition -> occupiedPosition.immutable() }.filter { occupiedPosition ->
            val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return@filter false

            level.getBlockState(occupiedPosition).block.explosionResistance >= 6e2f
         }.toList()

      return occupiedPositions.size <= (2) && occupiedPositions.contains(position)
   }

   private fun hasAdjacentCrystalBase(position: BlockPos): Boolean {
      val offsets = crystalOffsets.iterator()
      while (offsets.hasNext()) {
         val offset = offsets.next()
         val supportPosition = position.offset(offset).below()
         if (this.canPlaceCrystal(supportPosition)) {
            return true
         }
      }
      return false
   }

   private fun intersectsBedrock(target: Player): Boolean {
      val targetBounds = target.boundingBox
      val targetY = target.y
      val footBounds = targetBounds.setMaxY(targetY)
      return BlockPos.betweenClosedStream(footBounds).map { position -> position.immutable() }.allMatch { position ->
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return@allMatch false
         val state: BlockState = level.getBlockState(position)
         val bedrock: Block = Blocks.BEDROCK
         state.`is`(bedrock)
      }
   }

   override fun canMinePosition(position: BlockPos): Boolean {
      if (this.isProtectedSelfPosition(position)) {
         return false
      }

      return WorldUtils.getBlock(position) !== Blocks.COBWEB && super.canMinePosition(position)
   }

   private fun refreshProtectedPositions() {
      this.protectedPositions.clear()
      val movementMode = RotationManager.isMovementSyncEnabled()
      val playerSnapshot: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return

      val occupiedIterator = PlayerStateUtils.getSurroundPositions(playerSnapshot).iterator()

      while (occupiedIterator.hasNext()) {
         val occupiedPosition = occupiedIterator.next()

         if (!PlayerStateUtils.isLocalLowProfile() && !movementMode) {
            this.protectedPositions.add(occupiedPosition.above())
         }

         this.protectedPositions.add(occupiedPosition)
      }

      if (!movementMode) {
         val playerHeight = playerSnapshot.boundingBox.ysize
         val upperOffset = Math.ceil(playerHeight).toInt()
         val upperPlayer: LocalPlayer = playerSnapshot
         val upperPositions: Stream<BlockPos> = PlayerStateUtils.getOccupiedBlockPositions(upperPlayer).stream().map { position: BlockPos ->
               position.above(upperOffset)
            }
         upperPositions.forEach { position -> this.protectedPositions.add(position) }
      }
   }

   private fun getPerpendicularDirections(direction: Direction): Array<Direction> {
      return when (direction) {
         Direction.NORTH, Direction.SOUTH -> arrayOf(Direction.EAST, Direction.WEST)
         Direction.EAST, Direction.WEST -> arrayOf(Direction.NORTH, Direction.SOUTH)
         else -> arrayOf()
      }
   }

   override fun collectCandidates(candidates: AutoMineCandidates) {
      this.refreshProtectedPositions()
      val target: Player? = this.autoMine.targetSelector.selectTarget()
      val fishSetting: Setting<Boolean> = this.autoMine.fishSetting
      val fishEnabled = fishSetting.value
      var fishPriority = false
      if (fishEnabled && candidates.priority ==
         (400)
      ) {
         fishPriority = candidates.secondaryPosition != null
      }
      if (target == null) {
         return
      }
      if (this.hasNoCollisionBelow(target)) {
         return
      }
      if (this.intersectsBedrock(target) && !fishPriority) {
         return
      }
      val targetCoordinates = target.position()
      val targetPosition = BlockPos.containing(targetCoordinates)
      val footPositions = ArrayList<BlockPos>(PlayerStateUtils.getSurroundPositions(target))
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      val baseBlock: Block = level.getBlockState(targetPosition).block
      var suppressPreviousSelection = baseBlock.explosionResistance >= 6e2f && baseBlock !== Blocks.AIR
      if (this.autoMine.miningTracker.hasRecentDelayElapsed(500L)) {
         val currentTrackerSnapshot: AutoMineMiningTracker = this.autoMine.miningTracker
         if (targetPosition == currentTrackerSnapshot.currentPosition) {
            suppressPreviousSelection = false
         }
      } else {
         suppressPreviousSelection = false
      }
      val selectedPair: FeetMiningPair = this.selectFeetPair(target, footPositions, suppressPreviousSelection, fishPriority)
      if (selectedPair.primaryPosition == null) {
         return
      }
      val updateSelectedPair = Consumer<AutoMineCandidates> { output ->
         if (output.secondaryPosition == null) {
            val secondarySnapshot = selectedPair.secondaryPosition
            output.secondaryPosition = secondarySnapshot
         }
         val primarySnapshot = selectedPair.primaryPosition
         output.primaryPosition = primarySnapshot
      }
      candidates.offer(600, updateSelectedPair)
   }

   private fun selectFeetPair(
      target: Player, footPositions: MutableList<BlockPos>, suppressPreviousBreak: Boolean, fishPriority: Boolean
   ): FeetMiningPair {
      val lowerBedrockCandidates = footPositions.stream().allMatch { position: BlockPos ->
               WorldUtils.getBlock(position) === Blocks.BEDROCK
            } && fishPriority

      val selfPosition = PlayerStateUtils.getLocalRoundedBlockPosition()
      val selfPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return FeetMiningPair(null, null)
      val selfOccupiedCount = PlayerStateUtils.getOccupiedBlockPositions(selfPlayer).size
      var primaryPosition: BlockPos? = null
      var secondaryPosition: BlockPos? = null
      var primaryIsCurrentMining = false

      // Ключи сортировки считаются один раз на позицию (Schwartzian), а не на
      // каждое сравнение: раньше каждый ключ звал canPlaceCrystal (перебор сущностей
      // уровня) и строил surround-набор — O(n·log n) тяжёлых вычислений вместо O(n).
      // Порядок сохранён: сначала идут «штрафные» ключи (пересечение с собой,
      // собственный surround, нет базы под кристалл), затем дальние позиции,
      // ближайшая к себе — последней (итератор ниже выбирает последней как primary).
      val selfBounds = selfPlayer.boundingBox
      val selfSurroundPositions = PlayerStateUtils.getSurroundPositions(selfPlayer)
      val distancePosition = selfPlayer.position()
      val maximumRangeDouble = (speedMine.rangeSetting.maximumValue ?: 0f).toDouble()
      val noBasePenalty = Math.pow(maximumRangeDouble, 2.0)

      val scoredPositions = ArrayList<ScoredPosition>(footPositions.size)
      for (position in footPositions) {
         val blockBounds = AABB(position)
         val sortKey: Double = if (selfBounds.intersects(blockBounds)) {
            Double.NEGATIVE_INFINITY
         } else if (selfSurroundPositions.contains(position)) {
            -Double.MAX_VALUE
         } else {
            var hasHorizontalCrystalBase = false
            for (offset in crystalOffsets) {
               if (offset.y == 0 && this.canPlaceCrystal(position.offset(offset).below())) {
                  hasHorizontalCrystalBase = true
                  break
               }
            }

            var distanceScore = position.distToCenterSqr(distancePosition)
            if (!hasHorizontalCrystalBase) {
               distanceScore += noBasePenalty
            }
            -distanceScore
         }
         scoredPositions.add(ScoredPosition(position, sortKey))
      }
      scoredPositions.sortWith(compareBy { scored -> scored.sortKey })

      val positions = scoredPositions.iterator()

      while (positions.hasNext()) {
         var position = positions.next().position

         if (!this.isAlreadyMining(position) &&
         selfPosition.below() == position && selfOccupiedCount == 1
         ) {
            continue
         }

         if (lowerBedrockCandidates) {
            position = position.below()
         }

         val recentDelayMillis = if (RotationManager.isMovementSyncEnabled()) 500L else 1000L
         var retainPreviousBreak = false
         if (WorldUtils.isAir(position)) {
            val previousSnapshot: SpeedMineModule = speedMine
            retainPreviousBreak = position == previousSnapshot.miningPos
         }
         val instantRebreak = speedMine.rebreakSetting.value == SpeedMineRebreakChoice.INSTANT
         var fastRebreak = false
         if (this.isAlreadyMining(position) && !WorldUtils.isAir(position)) {
            fastRebreak = speedMine.rebreakSetting.value == SpeedMineRebreakChoice.FAST
         }

         if (suppressPreviousBreak) {
            retainPreviousBreak = false
         }

         if (speedMine.hasCrystalDelayElapsed(recentDelayMillis) && instantRebreak) {
            val asyncSetting: Setting<Boolean> = speedMine.asyncSetting
            val async = asyncSetting.value
            if (!async) {
               retainPreviousBreak = false
            }
         }

         if (!(this.hasAdjacentCrystalBase(position) || fishPriority || instantRebreak || fastRebreak)) {
            continue
         }
         if (!(this.canMinePosition(position) || retainPreviousBreak)) {
            continue
         }
         if (this.isAlreadyMining(position)) {
            primaryPosition = position
            primaryIsCurrentMining = true
         } else if (primaryIsCurrentMining) {
            secondaryPosition = position
         } else {
            secondaryPosition = primaryPosition
            primaryPosition = position
         }
      }

      if (primaryPosition != null) {
         val cornersSetting: Setting<Boolean> = this.autoMine.cornersSetting
         if (cornersSetting.value) {
            if (this.autoMine.isExtraBreakEnabled()) {
               val adjacentPosition = this.findAdjacentCandidate(target, primaryPosition)
               if (adjacentPosition != null) {
                  secondaryPosition = adjacentPosition
               }
            }
         }
      }

      return FeetMiningPair(primaryPosition, secondaryPosition)
   }

   private fun findAdjacentCandidate(target: Player, referencePosition: BlockPos): BlockPos? {
      val targetCoordinates = target.position()
      val targetPosition = BlockPos.containing(targetCoordinates)
      var matchedDirection: Direction? = null
      val directions = Plane.HORIZONTAL.iterator()
      while (directions.hasNext()) {
         val direction = directions.next()
         if (targetPosition.relative(direction) == referencePosition) {
            matchedDirection = direction
            break
         }
      }
      if (matchedDirection == null) {
         return null
      }

      for (perpendicularDirection in this.getPerpendicularDirections(matchedDirection)) {
         val adjacentPosition = referencePosition.relative(perpendicularDirection)
         if (WorldUtils.isAir(adjacentPosition)) {
            val supportPosition = adjacentPosition.below()
            if (WorldUtils.isBedrockOrObsidian(supportPosition)) {
               return null
            }
         }
         if (this.canMinePosition(adjacentPosition)) {
            val lowerPosition = adjacentPosition.below()
            if (WorldUtils.isBedrockOrObsidian(lowerPosition)) {
               return adjacentPosition
            }
         }
      }
      return null
   }

   override fun isEnabled(): Boolean {
      return this.autoMine.feetSetting.value
   }

   private data class FeetMiningPair(
      val primaryPosition: BlockPos?,
      val secondaryPosition: BlockPos?
   )

   private data class ScoredPosition(
      val position: BlockPos,
      val sortKey: Double
   )

   companion object {
      private lateinit var autoCrystal: AutoCrystalModule
      private lateinit var speedMine: SpeedMineModule
      private val crystalOffsets: List<Vec3i> = listOf(
         Vec3i(1, 0, 0),
         Vec3i(0, 0, 1),
         Vec3i(0, 1, 0),
         Vec3i(0, 0, -1),
         Vec3i(-1, 0, 0)
      )

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         autoCrystal = registry.getModule(
            AutoCrystalModule::class.java
         ) as AutoCrystalModule
         speedMine = registry.getModule(
            SpeedMineModule::class.java
         ) as SpeedMineModule
      }
   }
}
