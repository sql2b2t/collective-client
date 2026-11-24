package me.collective.client.utils.inventory

import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.combat.AutoMineMiningTracker
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

abstract class AutoMineProviderBase(
   @JvmField protected val autoMine: AutoMineModule
) : ClientAccess,
   AutoMineProvider {

   protected fun hasNoCollisionBelow(entity: Entity): Boolean {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return true
      val collisionBounds: AABB = entity.boundingBox.expandTowards(0.0, -0.1, 0.0)
      return level.noBlockCollision(entity, collisionBounds)
   }

   protected open fun canMinePosition(position: BlockPos): Boolean {
      val miningTracker: AutoMineMiningTracker = this.autoMine.miningTracker
      if (position == miningTracker.recentPosition) {
         return false
      }
      val rangeCheck = speedMine ?: return false
      if (!rangeCheck.isInMiningRange(position)) {
         return false
      }
      val blockCheck = speedMine ?: return false
      if (!blockCheck.isBlockAllowed(position)) {
         return false
      }
      val strictDirectionSetting: Setting<Boolean> = this.autoMine.strictDirectionSetting
      if (strictDirectionSetting.value) {
         if (BlockInteractionUtils.getStrictDirections(position).isEmpty()) {
            return false
         }
      }
      val raytraceSetting: Setting<Boolean> = this.autoMine.raytraceSetting
      if (raytraceSetting.value) {
         val raytracePoints: List<Vec3> = BlockSampleMode.X8.sampleBlock(position)
         if (!WorldUtils.canSeeAny(raytracePoints)) {
            return false
         }
      }
      if (this.autoMine.hasAssistantBreakProgress(position)) {
         return false
      }
      return WorldUtils.canMineBlock(position)
   }

   protected fun isAlreadyMining(position: BlockPos?): Boolean {
      val sm = speedMine ?: return false
      return position != null && (position == sm.miningPos || position == sm.rebreakPos)
   }

   override fun isEnabled(): Boolean {
      return false
   }

   companion object {
      private var speedMine: SpeedMineModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         speedMine = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule?
      }
   }
}
