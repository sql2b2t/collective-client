package me.collective.client.modules.render


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.MouseHandlerOnScrollEvent
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.FrameTiming
import me.collective.client.utils.KeyUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.util.Mth

class ViewClipModule : Module("ViewClip", "Allows you to clip into blocks using 3rd person camera.", ModuleCategory.RENDER) {
   @JvmField
   val skipSetting: Setting<SkipChoice>
   @JvmField
   val rangeSetting: Setting<Float>
   @JvmField
   val smoothSetting: Setting<Boolean>
   @JvmField
   val scrollSetting: Setting<Boolean>
   @JvmField
   val clipAnimation: ClampedInterpolatedFloat
   private var scrollTargetOffset: Float = 0.0F
   private var scrollOffset: Float = 0.0F

   fun getCameraDistance(): Float {
      val distance: Float = this.rangeSetting.getValue()!! + this.scrollOffset
      return Mth.clamp(distance, 0.1F, 1e+01F)
   }

   @EventHandler
   private fun onMouseHandlerOnScrollEvent(event: MouseHandlerOnScrollEvent) {
      val enabled: Boolean = this.scrollSetting.getValue()!!
      if (!enabled) {
         return
      }
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      if (!camera.isDetached()) {
         return
      }
      if (!KeyUtils.isPhysicalKeyDown(341)) {
         return
      }
      val currentTarget: Float = this.scrollTargetOffset
      val nextTarget: Float = currentTarget - event.verticalOffset.toFloat()
      val minimumRangeValue: Float = this.rangeSetting.getValue()!!
      val minimumOffset: Float = -minimumRangeValue
      val maximum: Float = this.rangeSetting.getMaximumValue()!!
      val currentRangeValue: Float = this.rangeSetting.getValue()!!
      val maximumOffset: Float = maximum - currentRangeValue
      this.scrollTargetOffset = Mth.clamp(nextTarget, minimumOffset, maximumOffset)
      event.cancel()
   }

   fun shouldSkipCameraType(cameraType: CameraType): Boolean {
      if (!this.isToggled()) {
         return false
      }
      val skip: Setting<SkipChoice> = this.skipSetting
      if (skip.getValue() == SkipChoice.NONE) {
         return false
      }
      return if (this.skipSetting.getValue() == SkipChoice.FRONT) {
         cameraType == CameraType.THIRD_PERSON_FRONT
      } else {
         cameraType == CameraType.THIRD_PERSON_BACK
      }
   }

   @EventHandler
   private fun onRenderFrame(event: WorldRenderEvent.Main) {
      val timing: FrameTiming = ClientContext.frameTiming
      val frameFactor: Float = timing.getFrameFactor(1.0F)
      val currentOffset: Float = this.scrollOffset
      val difference: Float = this.scrollTargetOffset - this.scrollOffset
      val exponent: Double = (-1e+01F * frameFactor).toDouble()
      this.scrollOffset = currentOffset + difference * (1.0F - Math.exp(exponent).toFloat())
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      if (camera.isDetached()) {
         val animation: ClampedInterpolatedFloat = this.clipAnimation
         animation.setTarget(true)
      } else {
         val animation: ClampedInterpolatedFloat = this.clipAnimation
         animation.setValue(0.0F)
         this.scrollTargetOffset = 0.0F
         this.scrollOffset = 0.0F
      }
   }

   init {
      this.skipSetting = this.add(EnumSetting<SkipChoice>("Skip", SkipChoice.NONE))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.5F, 0.1F, 10.0F))
      this.smoothSetting = this.add(BooleanSetting("Smooth", true))
      this.scrollSetting = this.add(BooleanSetting("Scroll", false))

      this.clipAnimation = ClampedInterpolatedFloat(1.3F, true)

      this.setDrawn(false)
   }

   enum class SkipChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      FRONT("Front"),
      BACK("Back");

      override fun getName(): String {
         return this.displayName
      }
   }
}
