package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.StepHeightEvent
import me.collective.client.utils.network.PacketSender
import me.collective.client.render.FontRenderer
import me.collective.client.managers.TickRateController
import me.collective.client.managers.HoleManager
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.ArrayList
import java.util.function.Predicate
import net.minecraft.core.BlockPos

class StepModule : Module("Step", "Allows you to step up blocks without jumping.", ModuleCategory.MOVEMENT) {
   companion object {
      private var holeSnap: HoleSnapModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         holeSnap = registry.getModule(HoleSnapModule::class.java) as HoleSnapModule
      }
   }

   val modeSetting: Setting<ModeChoice>
   private val heightSetting: Setting<Float>
   private val safeDisableSetting: Setting<Boolean>
   val useTimerSetting: Setting<Boolean>
   private val timerSpeedSetting: Setting<Float>
   private var usingStepTimer: Boolean = false
   private var stepCount: Int = 0

   override fun getInfo(): String? {
      return FontRenderer.capitalize(this.modeSetting.value)
   }

   override fun onDisable() {
      this.usingStepTimer = false
      this.stepCount = 0
   }

   @EventHandler(priority = -100)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      if (this.stepCount <= 0) {
         return
      }
      val holes: HoleManager = ClientContext.holeManager
      val position: BlockPos = PlayerStateUtils.getLocalRoundedBlockPosition()
      if (!holes.intersectsHole(position)) {
         return
      }
      if (this.safeDisableSetting.value) {
         this.disable()
      }
   }

   @EventHandler
   private fun onStepHeight(event: StepHeightEvent) {
      if (holeSnap!!.shouldPauseStep()) {
         return
      }
      if (!ClientAccess.collectiveMinecraft.player!!.onGround() || PlayerStateUtils.getTrackedFallDistance() > 0.0f ||
         !ClientAccess.collectiveMinecraft.player!!.verticalCollision) {
         val timer: TickRateController = ClientContext.tickRateController
         timer.releaseMultiplier(this)
         return
      }
      if (EventStage.Pre == event.stage) {
         val height: Setting<Float> = this.heightSetting
         val requestedHeight: Float = height.value
         event.height = requestedHeight
      }

      if (event.stage != EventStage.Post) {
         return
      }

      val actualHeight: Double = event.height.toDouble()
      if (actualHeight <= 0.6) {
         val timer: TickRateController = ClientContext.tickRateController
         timer.releaseMultiplier(this)
         return
      }

      if (this.modeSetting.value == ModeChoice.VANILLA) {
         this.stepCount++
         return
      }

      val offsets: ArrayList<Double> = ArrayList()
      if (this.useTimerSetting.value) {
         if (!PlayerStateUtils.isInCobweb(ClientAccess.collectiveMinecraft.player!!)) {
            val timer: TickRateController = ClientContext.tickRateController
            val requestedSpeed: Float = this.timerSpeedSetting.value
            timer.requestMultiplier(this, requestedSpeed)
            this.usingStepTimer = true
         }
      }

      this.stepCount++
      val height: Float = event.height
      if (height <= 1.0f) {
         offsets.addAll(listOf(0.42 * height, 0.75 * height))
      } else if (height <= 1.5) {
         offsets.addAll(listOf(0.42, 0.75, 1.0, 1.16, 1.23, 1.2))
      } else if (height <= this.heightSetting.value) {
         offsets.addAll(listOf(0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43))
      }

      if (offsets.isEmpty()) {
         return
      }
      for (offset in offsets) {
         val x: Double = ClientAccess.collectiveMinecraft.player!!.getX()
         val baseY: Double = ClientAccess.collectiveMinecraft.player!!.getY()
         val y: Double = baseY + offset
         val z: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
         val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
         PacketSender.sendPosition(x, y, z, onGround)
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      this.usingStepTimer = false
   }

   fun isUsingTimer(): Boolean {
      return this.usingStepTimer
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.VANILLA))
      this.heightSetting = this.add(NumberSetting<Float>("Height", 2.0f, 1.0f, 4.0f).withUnit("m"))
      this.safeDisableSetting = this.add(BooleanSetting("SafeDisable", false))
      val timerVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.NORMAL }
      this.useTimerSetting = this.add(BooleanSetting("UseTimer", true, timerVisibility).markVisibilityDependency())
      val timerSpeedVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.NORMAL && this.useTimerSetting.isExpanded }
      this.timerSpeedSetting = this.add(NumberSetting<Float>("TimerSpeed", 0.3f, 0.01f, 1.0f, timerSpeedVisibility))
      this.stepCount = 0
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla"),
      NORMAL("Normal");

      override fun getName(): String {
         return this.displayName
      }
   }
}
