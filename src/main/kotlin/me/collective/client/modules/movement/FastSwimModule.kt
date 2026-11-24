package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.Axis
import net.minecraft.util.Mth

class FastSwimModule : Module("FastSwim", "Makes you move faster while in liquids.", ModuleCategory.MOVEMENT) {
   companion object {
      private var speedMineModule: SpeedMineModule? = null

      @JvmStatic
      fun bindReferencedModules(modules: ModuleRegistry) {
         speedMineModule = modules.getModule(SpeedMineModule::class.java) as SpeedMineModule
      }
   }

   private val glideSetting: Setting<Float>
   private val waterSetting: Setting<Boolean>
   private val waterSpeedSetting: Setting<Float>
   private val lavaSetting: Setting<Boolean>
   private val lavaSpeedSetting: Setting<Float>
   private val verticalSetting: Setting<Boolean>
   private val vSpeedSetting: Setting<Float>

   @JvmField
   val accelerateSetting: Setting<Boolean>

   @JvmField
   val accelMinSetting: Setting<Float>

   @JvmField
   val accelTimeSetting: Setting<Float>

   private var accelerationStartMillis: Long = 0

   private fun shouldSkipSwimming(): Boolean {
      if (!ClientAccess.collectiveMinecraft.player!!.isInLava()) {
         if (!PlayerStateUtils.isInWater(ClientAccess.collectiveMinecraft.player!!)) {
            return true
         }
      }
      if (ClientAccess.collectiveMinecraft.player!!.isInLava()) {
         if (!this.lavaSetting.value) {
            return true
         }
      }
      if (PlayerStateUtils.isInWater(ClientAccess.collectiveMinecraft.player!!)) {
         if (!this.waterSetting.value) {
            return true
         }
      }
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
         return true
      }
      if (!ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)) {
         return true
      }
      if (speedMineModule!!.isToggled()) {
         val miningProgress: Double = speedMineModule!!.getMiningProgress().toDouble()
         val nextMiningProgress: Double = miningProgress + speedMineModule!!.getTickBreakProgress()
         val threshold: Double = speedMineModule!!.damageSetting.value.toDouble()
         if (nextMiningProgress >= threshold - 0.1) {
            val miningTargetSnapshot: BlockPos? = speedMineModule!!.getMiningOrNearlyBrokenPos()
            if (miningTargetSnapshot != null) {
               if (WorldUtils.canMineBlock(miningTargetSnapshot)) {
                  return true
               }
            }
         }
      }
      return ClientContext.baritoneBridge.isPathingOrCalculating()
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {
      if (!this.shouldSkipSwimming()) {
         ClientAccess.collectiveMinecraft.player!!.avatarState().walkDist = 0.0f
      }
   }

   @EventHandler
   private fun onMove(event: LocalPlayerMoveEvent) {
      if (this.shouldSkipSwimming()) {
         this.accelerationStartMillis = System.currentTimeMillis()
         return
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartMillis = System.currentTimeMillis()
      }
      val verticalSpeed: Float = if (this.verticalSetting.value) 0.12f * this.vSpeedSetting.value else 0.0f
      val minimumSpeed: Float = 0.12f * this.accelMinSetting.value
      var horizontalSpeed: Double = 0.12 * this.lavaSpeedSetting.value
      if (this.waterSetting.value && PlayerStateUtils.isInWater(ClientAccess.collectiveMinecraft.player!!)) {
         horizontalSpeed = 0.12 * this.waterSpeedSetting.value
      }
      if (this.accelerateSetting.value && minimumSpeed < horizontalSpeed) {
         val elapsedMillis: Float = (System.currentTimeMillis() - this.accelerationStartMillis).toFloat()
         val accelerationSeconds: Float = this.accelTimeSetting.value
         val progress: Float = Mth.clamp(elapsedMillis / (accelerationSeconds * 1000.0f), 0.0f, 1.0f)
         horizontalSpeed = Math.min(minimumSpeed + (horizontalSpeed - minimumSpeed) * progress, horizontalSpeed)
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (player.input.keyPresses.jump()) {
         event.setY(verticalSpeed.toDouble())
         player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, verticalSpeed.toDouble()))
      } else if (player.input.keyPresses.shift()) {
         event.setY((-verticalSpeed).toDouble())
         player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -verticalSpeed.toDouble()))
         horizontalSpeed /= 2.0
      } else {
         event.setY(0.0)
         player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, 0.0))
         if (!player.verticalCollision && this.glideSetting.value != 0.0f) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, -this.glideSetting.value.toDouble(), 0.0))
            event.setY(player.getDeltaMovement().y)
         }
      }
      val horizontal: DoubleArray = MovementPhysics.applyHorizontalMotion(event, horizontalSpeed)
      player.setDeltaMovement(horizontal[0], player.getDeltaMovement().y, horizontal[1])
   }

   init {
      this.glideSetting = this.add(NumberSetting<Float>("Glide", 0.05f, 0.0f, 5.0f))
      this.waterSetting = this.add(BooleanSetting("Water", false).markVisibilityDependency())
      this.waterSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 3.0f).dependsOn(this.waterSetting))
      this.lavaSetting = this.add(BooleanSetting("Lava", true).markVisibilityDependency())
      this.lavaSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 3.0f).dependsOn(this.lavaSetting))
      this.verticalSetting = this.add(BooleanSetting("Vertical", false).markVisibilityDependency())
      this.vSpeedSetting = this.add(NumberSetting<Float>("VSpeed", 1.0f, 0.1f, 2.0f).dependsOn(this.verticalSetting))
      this.accelerateSetting = this.add(BooleanSetting("Accelerate", false).markVisibilityDependency())
      this.accelMinSetting = this.add(NumberSetting<Float>("AccelMin", 1.0f, 0.0f, 5.0f).dependsOn(this.accelerateSetting))
      val accelTime: NumberSetting<Float> = NumberSetting<Float>("AccelTime", 1.0f, 0.1f, 10.0f)
      accelTime.withUnit("s")
      this.accelTimeSetting = this.add(accelTime.dependsOn(this.accelerateSetting))
      var identitySetting: Setting<Float> = this.waterSpeedSetting
      identitySetting.setConfigName("WaterSpeed")
      identitySetting = this.lavaSpeedSetting
      identitySetting.setConfigName("LavaSpeed")
   }
}
