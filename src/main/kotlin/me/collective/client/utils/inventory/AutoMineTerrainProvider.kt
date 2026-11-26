package me.collective.client.utils.inventory

import net.minecraft.world.phys.Vec3
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.combat.AutoMineTargetSelector
import me.collective.client.modules.player.SpeedMineModule
import java.util.Comparator
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

class AutoMineTerrainProvider(module: AutoMineModule) : AutoMineProviderBase(module) {

   private fun hasClearColumn(position: BlockPos): Boolean {
      val abovePosition = position.above()
      if (!WorldUtils.isAir(abovePosition)) {
         return false
      }

      val columnBounds = AABB(position).expandTowards(0.0, 1.0, 0.0)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return true
      val entities = level.entitiesForRendering().iterator()
      while (entities.hasNext()) {
         val entity: Entity = entities.next()
         if (!entity.isRemoved && entity.isAlive && entity !is EndCrystal) {
            if (entity.boundingBox.intersects(columnBounds)) {
               return false
            }
         }
      }
      return true
   }

   private fun selectTerrainCandidate(target: Player, basePosition: BlockPos, referencePosition: BlockPos?): BlockPos? {
      val occupiedPositions = PlayerStateUtils.getSurroundPositions(target)
      val minimumPosition = basePosition.offset(-2, -1, -2)
      val maximumPosition = basePosition.offset(2, -1, 2)
      val scoredCandidates: List<TerrainMiningCandidate> = BlockPos.betweenClosedStream(minimumPosition, maximumPosition)
         .map { position -> position.immutable() }
         .filter { position -> !occupiedPositions.contains(position.above()) }
         .filter { position -> WorldUtils.isReplaceable(position) || this.canMinePosition(position) }
         .filter { position -> this.hasClearColumn(position.above()) || WorldUtils.isAir(position) }
         .map { position -> TerrainMiningCandidate.create(position, referencePosition, target) }
         .toList()
      if (scoredCandidates.stream().anyMatch { candidate ->
         WorldUtils.isReplaceable(candidate.position) && candidate.damage >= 6.0
      }) {
         return null
      }

      val selectedCandidate = scoredCandidates.stream()
         .max(Comparator.comparingDouble<TerrainMiningCandidate> { candidate -> candidate.getPriorityScore() }).orElse(null)
      if (selectedCandidate == null || WorldUtils.isAir(selectedCandidate.position)) {
         return null
      }
      if (!selectedCandidate.currentlyMining && selectedCandidate.damage <= 4.0) {
         return null
      }
      if (!WorldUtils.isBedrockOrObsidian(selectedCandidate.position) &&
         !WorldUtils.isReplaceable(selectedCandidate.position)
      ) {
         return selectedCandidate.position
      }
      return null
   }

   override fun collectCandidates(candidates: AutoMineCandidates) {
      val targetSelector: AutoMineTargetSelector = this.autoMine.targetSelector
      val target: Player? = targetSelector.selectTarget()
      if (target == null || this.hasNoCollisionBelow(target)) {
         return
      }

      val targetPosition = BlockPos.containing(target.position())
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      val baseBlock: Block = level.getBlockState(targetPosition).block
      val resistantBase = baseBlock.explosionResistance >= 6e2f && baseBlock !== Blocks.AIR
      val occupiedPolicy = PlayerStateUtils.getSurroundPositions(target).stream()
         .anyMatch { position -> !WorldUtils.isBedrockOrObsidian(position) }
      if (resistantBase && !occupiedPolicy) {
         return
      }

      if (candidates.primaryPosition == null) {
         return
      }
      if (candidates.primaryPosition!!.y != targetPosition.y) {
         return
      }

      val referencePosition = if (occupiedPolicy) candidates.secondaryPosition else candidates.primaryPosition
      val candidatePosition = this.selectTerrainCandidate(target, targetPosition, referencePosition)
      if (candidatePosition == null) {
         return
      }

      val priority: Int
      if (occupiedPolicy) {
         priority = 700
      } else {
         priority = 600
      }
      candidates.offer(priority) { output ->
         if (occupiedPolicy) {
            output.primaryPosition = candidatePosition
         } else {
            output.secondaryPosition = candidatePosition
         }
      }
   }

   override fun isEnabled(): Boolean {
      return this.autoMine.terrainSetting.value
   }

   private data class TerrainMiningCandidate(
      val position: BlockPos,
      val damage: Double,
      val currentlyMining: Boolean
   ) {
      companion object {
         fun create(position: BlockPos, referencePosition: BlockPos?, target: Player): TerrainMiningCandidate {
            val elevatedCenter = Vec3.atCenterOf(position).add(0.0, 0.5, 0.0)
            val targetBounds = target.boundingBox
            val ignoredSupport = position.below()
            val damage: Float = DamageUtils.getExplosionDamage(
               elevatedCenter, target, targetBounds, 6.0, true, referencePosition, ignoredSupport
            )
            val presenceSpeedMine = speedMine
            var currentlyMining = false
            if (presenceSpeedMine.extraBreakTarget != null) {
               currentlyMining = speedMine.extraBreakTarget!!.position == position
            }
            return TerrainMiningCandidate(position, damage.toDouble(), currentlyMining)
         }
      }

      fun getPriorityScore(): Double {
         return if (this.currentlyMining) Double.MAX_VALUE else this.damage
      }
   }

   companion object {
      private lateinit var speedMine: SpeedMineModule

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         speedMine = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule
      }
   }
}
