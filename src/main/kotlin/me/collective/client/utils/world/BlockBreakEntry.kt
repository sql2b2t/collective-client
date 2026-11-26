package me.collective.client.utils.world

import net.minecraft.world.phys.Vec3
import me.collective.client.utils.ElapsedTimer

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity

class BlockBreakEntry(entityId: Int, @get:JvmName("getPositionProp") val position: BlockPos, extraBreak: Boolean) {
   private val inactivityTimer = ElapsedTimer()
   private val progressTimer = ElapsedTimer()
   private val entityId: Int = entityId
   private var extraBreak: Boolean = extraBreak
   private var broken = false
   private var _progress = 0.0F
   private var previousProgress = 0.0F
   private var airTicks = 0

   var progress: Float
      @JvmName("getProgressProp") get() = getProgress()
      @JvmName("setProgressProp") set(value) = setProgress(value)

   fun getPosition(): BlockPos = this.position

   fun markBroken() {
      broken = true
   }

   fun getAirTicks(): Int {
      return airTicks
   }

   fun setAirTicks(airTicks: Int) {
      this.airTicks = airTicks
   }

   fun resetInactivityTimer() {
      inactivityTimer.reset()
   }

   fun isExtraBreak(): Boolean {
      return extraBreak
   }

   fun getProgressTimer(): ElapsedTimer {
      return progressTimer
   }

   fun getEntity(): Entity? {
      val level = Minecraft.getInstance().level ?: return null
      val entityIdSnapshot = entityId
      return level.getEntity(entityIdSnapshot)
   }

   fun isBroken(): Boolean {
      return broken
   }

   fun isExpired(): Boolean {
      if (this._progress == 1.0F) {
         if (inactivityTimer.hasElapsed(2000L)) {
            return true
         }
         if (isExtraBreak()) {
            return true
         }
      }

      if (isExtraBreak() && isBroken()) {
         return true
      }

      val entity = getEntity() ?: return true

      val blockCenter = Vec3.atCenterOf(position)
      val distanceSquared = entity.eyePosition.distanceToSqr(blockCenter)
      return distanceSquared >= 64.0
   }

   fun getProgress(): Float {
      return this._progress
   }

   fun getEntityId(): Int {
      return entityId
   }

   fun getInterpolatedProgress(partialTick: Float): Float {
      val previous = previousProgress
      val current = this._progress
      return Mth.lerp(partialTick, previous, current)
   }

   fun setProgress(progress: Float) {
      if (this._progress == progress) {
         return
      }
      previousProgress = if (progress == 0.0F) 0.0F else this._progress

      resetInactivityTimer()
      this._progress = Mth.clamp(progress, 0.0F, 1.0F)
   }
}
