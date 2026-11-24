package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MouseHandlerOnScrollEvent
import me.collective.client.events.bus.EventBus
import me.collective.client.utils.FloatAnimation
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.util.Mth

class ZoomModule : Module("Zoom", "Zooms your camera in.", ModuleCategory.RENDER) {
   private val smoothSetting: Setting<Boolean>
   @JvmField
   internal val smoothCameraSetting: Setting<Boolean>
   private val scrollSetting: Setting<Boolean>
   private val amountSetting: Setting<Int>
   private val optionsUpdater: ZoomOptionsUpdater
   @JvmField
   internal var baselineFov: Int = 0
   @JvmField
   protected var savedSmoothCamera: Boolean = false
   @JvmField
   internal var fovUpdateActive: Boolean = false
   private val zoomAnimation: FloatAnimation
   private val scrollAnimation: FloatAnimation

   internal fun calculateFov(): Int {
      val baseline: Float = this.baselineFov.toFloat()
      val divisor: Int = this.amountSetting.getValue()!!
      val fovDifference: Int = 75 / divisor - this.baselineFov
      val distance: Float = Math.abs(fovDifference).toFloat()
      val fov: Int = (baseline - distance * this.getZoomBlend()).toInt()
      val scrollEnabled: Boolean = this.scrollSetting.getValue()!!
      if (!scrollEnabled) {
         return fov
      }
      var adjustedFov: Float = fov.toFloat()
      val animation = this.scrollAnimation
      adjustedFov -= animation.getValue()
      return Mth.clamp(adjustedFov, 1e+01F, 1.5e+02F).toInt()
   }

   fun getZoomBlend(): Float {
      val animation = this.zoomAnimation
      val progress: Double = (1.0F - animation.getValue()).toDouble()
      return (1.0 - Math.pow(progress, 4.0)).toFloat()
   }

   override fun onEnable() {
      this.savedSmoothCamera = ClientAccess.collectiveMinecraft.options.smoothCamera
      if (this.isWorldUnavailable()) {
         this.disable()
         return
      }
      val bus: EventBus = ClientAccess.eventBus
      val updater: ZoomOptionsUpdater = this.optionsUpdater
      bus.subscribe(updater)
      if (!this.fovUpdateActive) {
         val options: Options = ClientAccess.collectiveMinecraft.options
         val fov: OptionInstance<Int> = options.fov()
         this.baselineFov = fov.get()!!
      }
      val animation = this.zoomAnimation
      val duration: Long = this.getSmoothingDuration(200).toLong()
      animation.setTarget(1.0F, duration)
   }

   override fun onDisable() {
      val primaryAnimation = this.zoomAnimation
      val primaryDuration: Long = this.getSmoothingDuration(100).toLong()
      primaryAnimation.setTarget(0.0F, primaryDuration)
      val scroll = this.scrollAnimation
      val scrollDuration: Long = this.getSmoothingDuration(100).toLong()
      scroll.setTarget(0.0F, scrollDuration)
      ClientAccess.collectiveMinecraft.options.smoothCamera = this.savedSmoothCamera
   }

   private fun getSmoothingDuration(duration: Int): Int {
      return if (!this.smoothSetting.getValue()!!) 0 else duration
   }

   @EventHandler
   private fun onMouseHandlerOnScrollEvent(event: MouseHandlerOnScrollEvent) {
      if (event.verticalOffset == 0.0) {
         return
      }
      val enabled: Boolean = this.scrollSetting.getValue()!!
      if (!enabled) {
         return
      }
      val baseline: Float = this.baselineFov.toFloat()
      val divisor: Int = this.amountSetting.getValue()!!
      val fovDifference: Int = 75 / divisor - this.baselineFov
      val distance: Float = Math.abs(fovDifference).toFloat()
      val fov: Int = (baseline - distance * this.getZoomBlend()).toInt()
      val currentValue: Double = this.scrollAnimation.getValue().toDouble()
      val delta: Double = event.verticalOffset
      val target: Float = (currentValue + delta * 10.0).toFloat()
      val minimum: Float = (fov - this.baselineFov).toFloat()
      val maximum: Float = (fov - 10).toFloat()
      val clampedTarget: Int = Mth.clamp(target, minimum, maximum).toInt()
      val targetScroll = this.scrollAnimation
      val scrollTarget: Float = clampedTarget.toFloat()
      val duration: Long = this.getSmoothingDuration(100).toLong()
      targetScroll.setTarget(scrollTarget, duration)
      event.cancel()
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      if (this.isWorldUnavailable()) {
         this.disable()
      }
   }

   init {
      this.smoothSetting = this.add(BooleanSetting("Smooth", true))
      this.smoothCameraSetting = this.add(BooleanSetting("SmoothCamera", true))
      this.scrollSetting = this.add(BooleanSetting("Scroll", false))
      this.amountSetting = this.add(NumberSetting<Int>("Amount", 2, 2, 5))
      this.optionsUpdater = ZoomOptionsUpdater(this)
      this.zoomAnimation = FloatAnimation()
      this.scrollAnimation = FloatAnimation()
   }
}
