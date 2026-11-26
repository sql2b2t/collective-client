package me.collective.client.utils.inventory

import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.combat.AutoMineTargetSelector
import me.collective.client.modules.combat.AutoTrapModule
import me.collective.client.settings.Setting
import java.util.Comparator
import java.util.Optional
import java.util.function.Consumer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

class AutoMineHeadProvider(module: AutoMineModule) : AutoMineProviderBase(module) {

   private fun isAlternateHeadModeEnabled(): Boolean {
      return antiCheat.is2b2tEnabled()
   }

   override fun collectCandidates(candidates: AutoMineCandidates) {
      val targetSelector: AutoMineTargetSelector = this.autoMine.targetSelector
      val target: Player? = targetSelector.selectTarget()
      if (target == null || this.hasNoCollisionBelow(target)) {
         return
      }

      val inCrawlSetting: Setting<Boolean> = this.autoMine.inCrawlSetting
      val inCrawl = inCrawlSetting.value
      if (!inCrawl && target.isVisuallyCrawling()) {
         return
      }

      val occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(target)
      val targetBounds = target.boundingBox
      val extraBreak = this.autoMine.isExtraBreakEnabled()
      val floorBounds = targetBounds.setMaxY(targetBounds.minY)
      val intersectsBedrock = BlockPos.betweenClosedStream(floorBounds).anyMatch { position ->
         BlockInteractionUtils.getBlock(position) === Blocks.BEDROCK
      }
      val insetBounds = targetBounds.deflate(0.05, 0.05, 0.05)
      if (!BlockPos.betweenClosedStream(insetBounds).filter { position ->
         BlockInteractionUtils.getBlock(position).explosionResistance >= 6e2f &&
         !this.isAlternateHeadModeEnabled() && BlockInteractionUtils.getBlock(position) !== Blocks.AIR
      }.toList().isEmpty()) {
         return
      }

      val upperBounds = targetBounds.setMaxY(targetBounds.maxY + 0.5)
      val upperResult: Optional<BlockPos> = BlockPos.betweenClosedStream(upperBounds)
         .map { position -> position.immutable() }.filter { position -> this.isHeadCandidateValid(position) }
         .max(Comparator.comparing<BlockPos, Boolean> { position -> this.isAlreadyMining(position) })
      var primaryPosition: BlockPos? = upperResult.orElse(null)
      val headResult: Optional<BlockPos> = PlayerStateUtils.getSurroundPositions(target).stream()
         .map { position -> position.immutable() }.map { position -> position.above() }.filter { position -> this.isHeadCandidateValid(position) }
         .max(Comparator.comparing<BlockPos, Int> { position -> this.getCandidatePriority(position, candidates) })
      var secondaryPosition: BlockPos? = headResult.orElse(null)
      if (this.isAlternateHeadModeEnabled() && intersectsBedrock) {
         secondaryPosition = null
      }
      if (primaryPosition == null && secondaryPosition != null && !this.isAlternateHeadModeEnabled()) {
         primaryPosition = secondaryPosition
         secondaryPosition = null
      }
      if (primaryPosition == null) {
         return
      }

      val selectedPrimary: BlockPos = primaryPosition
      val selectedSecondary: BlockPos? = secondaryPosition
      if (this.isAlternateHeadModeEnabled()) {
         val alternatePriority: Int
         if (intersectsBedrock) {
            if (extraBreak) {
               alternatePriority = 900
            } else {
               alternatePriority = 1000
            }
         } else {
            alternatePriority = if (extraBreak) 850 else 950
         }
         val alternateUpdate = Consumer<AutoMineCandidates> { output ->
            if (!extraBreak) {
               output.primaryPosition = selectedPrimary
            } else if (selectedSecondary != null) {
               output.secondaryPosition = selectedPrimary
               output.primaryPosition = selectedSecondary
            } else {
               output.primaryPosition = selectedPrimary
            }
         }
         candidates.offer(alternatePriority, alternateUpdate)
         return
      }

      var normalPriority = 599
      val priorityPosition: BlockPos = primaryPosition
      if (this.isAlreadyMining(priorityPosition)) {
         normalPriority = 650
      }
      val trapOverrideSetting: Setting<Boolean> = this.autoMine.trapOverrideSetting
      if (trapOverrideSetting.value) {
         if (autoTrap.isToggled()) {
            normalPriority = 650
         }
      }
      if (target.isVisuallyCrawling() && occupiedPositions.size == 1) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
         val selfPosition = ClientAccess.collectiveMinecraft.player?.blockPosition() ?: return
         if (level.getBlockState(selfPosition).isAir) {
            val crawlUpdate = Consumer<AutoMineCandidates> { output -> output.secondaryPosition = selectedPrimary }
            candidates.offer(650, crawlUpdate)
            return
         }
      }
      val normalUpdate = Consumer<AutoMineCandidates> { output -> output.primaryPosition = selectedPrimary }
      candidates.offer(normalPriority, normalUpdate)
   }

   private fun isHeadCandidateValid(position: BlockPos): Boolean {
      if (this.intersectsPlayer(position)) {
         return false
      }
      val baseAllowed = BlockInteractionUtils.getBlock(position) === Blocks.OBSIDIAN || this.isAlternateHeadModeEnabled()
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
      val abovePosition = position.above()
      val aboveAllowed = level.isEmptyBlock(abovePosition) || this.isAlternateHeadModeEnabled()
      if (!WorldUtils.canMineBlock(position) || !baseAllowed) {
         if (!this.isAlreadyMining(position)) {
            return false
         }
      }
      return aboveAllowed
   }

   private fun intersectsPlayer(position: BlockPos): Boolean {
      val blockBounds = AABB(position)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
      val players = level.players().iterator()
      while (players.hasNext()) {
         val playerBounds = players.next().boundingBox
         if (BoundingBoxUtils.hasVolumeOverlap(playerBounds, blockBounds)) {
            return true
         }
      }
      return false
   }

   private fun getCandidatePriority(position: BlockPos, candidates: AutoMineCandidates): Int {
      if (this.isAlreadyMining(position)) {
         return 2
      }
      if (position != candidates.secondaryPosition && position != candidates.primaryPosition) {
         return 0
      }
      return 1
   }

   override fun isEnabled(): Boolean {
      return this.autoMine.headSetting.value
   }

   companion object {
      private lateinit var autoTrap: AutoTrapModule
      private lateinit var antiCheat: AntiCheatModule

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         autoTrap = registry.getModule(AutoTrapModule::class.java) as AutoTrapModule
         antiCheat = registry.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }
   }
}
