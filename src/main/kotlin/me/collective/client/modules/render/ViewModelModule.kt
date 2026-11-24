package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.gui.screens.ViewModelEditorScreen
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import org.joml.Vector3f

class ViewModelModule : Module("ViewModel", "Transforms your 1st person view model.", ModuleCategory.RENDER) {
   private val adjustSetting: Setting<Boolean>
   @JvmField
   val instantSwapSetting: Setting<Boolean>
   @JvmField
   val eatingSetting: Setting<Boolean>
   @JvmField
   val lighterSetting: Setting<Boolean>
   private val mainHandSetting: Setting<Boolean>
   @JvmField
   val armSetting: Setting<Boolean>
   @JvmField
   val mainXSetting: Setting<Float>
   @JvmField
   val mainYSetting: Setting<Float>
   @JvmField
   val mainZSetting: Setting<Float>
   @JvmField
   val mainScaleXSetting: Setting<Float>
   @JvmField
   val mainScaleYSetting: Setting<Float>
   @JvmField
   val mainScaleZSetting: Setting<Float>
   @JvmField
   val mainRotateXSetting: Setting<Float>
   @JvmField
   val mainRotateYSetting: Setting<Float>
   @JvmField
   val mainRotateZSetting: Setting<Float>
   private val offHandSetting: Setting<Boolean>
   @JvmField
   val offXSetting: Setting<Float>
   @JvmField
   val offYSetting: Setting<Float>
   @JvmField
   val offZSetting: Setting<Float>
   @JvmField
   val offScaleXSetting: Setting<Float>
   @JvmField
   val offScaleYSetting: Setting<Float>
   @JvmField
   val offScaleZSetting: Setting<Float>
   @JvmField
   val offRotateXSetting: Setting<Float>
   @JvmField
   val offRotateYSetting: Setting<Float>
   @JvmField
   val offRotateZSetting: Setting<Float>
   @JvmField
   val miscSetting: Setting<Boolean>
   @JvmField
   val eatMultiplierSetting: Setting<Float>
   @JvmField
   val noSwaySetting: Setting<Boolean>
   @JvmField
   val noTridentAnimSetting: Setting<Boolean>
   @JvmField
   val swingProgressSetting: Setting<Boolean>
   @JvmField
   val staticSetting: Setting<Boolean>
   private val mainHandSwingSetting: Setting<Boolean>
   @JvmField
   val mainHandSwingProgressAmountSetting: Setting<Float>
   private val offHandSwingSetting: Setting<Boolean>
   @JvmField
   val offHandSwingProgressAmountSetting: Setting<Float>
   @JvmField
   val viewModelFovSetting: Setting<Boolean>
   @JvmField
   val fovAmountSetting: Setting<Int>
   private val scaleVector: Vector3f

   fun getScale(hand: InteractionHand): Vector3f {
      if (hand == InteractionHand.MAIN_HAND) {
         val mainScale = this.scaleVector
         val mainX: Float = this.mainScaleXSetting.getValue()
         val mainY: Float = this.mainScaleYSetting.getValue()
         val mainZ: Float = this.mainScaleZSetting.getValue()
         return mainScale.set(mainX, mainY, mainZ)
      } else {
         val offScale = this.scaleVector
         val offX: Float = this.offScaleXSetting.getValue()
         val offY: Float = this.offScaleYSetting.getValue()
         val offZ: Float = this.offScaleZSetting.getValue()
         return offScale.set(offX, offY, offZ)
      }
   }

   fun applySwingProgressOverride(hand: InteractionHand, originalProgress: Float): Float {
      if (!this.isToggled() || !this.swingProgressSetting.getValue()) {
         return originalProgress
      }
      if (hand == InteractionHand.MAIN_HAND && this.mainHandSwingSetting.getValue()) {
         val mainStatic: Boolean = this.staticSetting.getValue()
         val mainProgress: Float = if (mainStatic) 0.0F else originalProgress
         val mainMinimum: Float = this.mainHandSwingProgressAmountSetting.getValue()
         return Math.max(mainProgress, mainMinimum)
      }
      if (hand == InteractionHand.OFF_HAND && this.offHandSwingSetting.getValue()) {
         val offStatic: Boolean = this.staticSetting.getValue()
         val offProgress: Float = if (offStatic) 0.0F else originalProgress
         val offMinimum: Float = this.offHandSwingProgressAmountSetting.getValue()
         return Math.max(offProgress, offMinimum)
      }
      return originalProgress
   }

   init {
      this.adjustSetting = this.add(BooleanSetting("Adjust", false))
      this.instantSwapSetting = this.add(BooleanSetting("InstantSwap", false))
      this.eatingSetting = this.add(BooleanSetting("Eating", true))
      this.lighterSetting = this.add(BooleanSetting("Lighter", false))
      this.mainHandSetting = this.add(BooleanSetting("MainHand", true).markAsGroup().markVisibilityDependency())
      this.armSetting = this.add(BooleanSetting("Arm", false).dependsOn(this.mainHandSetting))
      this.mainXSetting = this.add(NumberSetting<Float>("MainX", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting))
      this.mainYSetting = this.add(NumberSetting<Float>("MainY", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting))
      this.mainZSetting = this.add(NumberSetting<Float>("MainZ", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting))
      this.mainScaleXSetting = this.add(NumberSetting<Float>("MainScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting))
      this.mainScaleYSetting = this.add(NumberSetting<Float>("MainScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting))
      this.mainScaleZSetting = this.add(NumberSetting<Float>("MainScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting))
      this.mainRotateXSetting = this.add(NumberSetting<Float>("MainRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting))
      this.mainRotateYSetting = this.add(NumberSetting<Float>("MainRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting))
      this.mainRotateZSetting = this.add(NumberSetting<Float>("MainRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting))
      this.offHandSetting = this.add(BooleanSetting("OffHand", true).markAsGroup().markVisibilityDependency())
      this.offXSetting = this.add(NumberSetting<Float>("OffX", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting))
      this.offYSetting = this.add(NumberSetting<Float>("OffY", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting))
      this.offZSetting = this.add(NumberSetting<Float>("OffZ", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting))
      this.offScaleXSetting = this.add(NumberSetting<Float>("OffScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting))
      this.offScaleYSetting = this.add(NumberSetting<Float>("OffScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting))
      this.offScaleZSetting = this.add(NumberSetting<Float>("OffScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting))
      this.offRotateXSetting = this.add(NumberSetting<Float>("OffRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting))
      this.offRotateYSetting = this.add(NumberSetting<Float>("OffRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting))
      this.offRotateZSetting = this.add(NumberSetting<Float>("OffRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting))
      this.miscSetting = this.add(BooleanSetting("Misc", false).markAsGroup().markVisibilityDependency())
      this.eatMultiplierSetting = this.add(NumberSetting<Float>("EatMultiplier", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting))
      this.noSwaySetting = this.add(BooleanSetting("NoSway", false).dependsOn(this.miscSetting))
      this.noTridentAnimSetting = this.add(BooleanSetting("NoTridentAnim", false).dependsOn(this.miscSetting))
      this.swingProgressSetting = this.add(BooleanSetting("SwingProgress", false).markVisibilityDependency().dependsOn(this.miscSetting))
      this.staticSetting = this.add(BooleanSetting("Static", true).dependsOn(this.miscSetting, this.swingProgressSetting))
      this.mainHandSwingSetting = this.add(BooleanSetting("MainHand", false).dependsOn(this.miscSetting, this.swingProgressSetting))
      this.mainHandSwingProgressAmountSetting = this.add(NumberSetting<Float>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.mainHandSwingSetting))
      this.offHandSwingSetting = this.add(BooleanSetting("OffHand", false).dependsOn(this.miscSetting, this.swingProgressSetting))
      this.offHandSwingProgressAmountSetting = this.add(NumberSetting<Float>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.offHandSwingSetting))
      this.viewModelFovSetting = this.add(BooleanSetting("ViewModelFov", false).markVisibilityDependency().dependsOn(this.miscSetting))
      this.fovAmountSetting = this.add(NumberSetting<Int>("FovAmount", 90, 70, 180).dependsOn(this.miscSetting, this.viewModelFovSetting))

      this.scaleVector = Vector3f(1.0F)

      this.mainHandSwingSetting.setConfigName("SwingMainHand")

      this.offHandSwingSetting.setConfigName("SwingOffHand")

      this.offHandSwingProgressAmountSetting.setConfigName("SwingProgressAmountOffHand")
      val callbackSetting = this.adjustSetting
      val callback = Runnable {
         val openAdjust: Boolean = this.adjustSetting.getValue()
         if (openAdjust) {
            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            val screen = ViewModelEditorScreen(this)
            minecraft.setScreen(screen)
            this.adjustSetting.setValue(false)
         }
      }
      callbackSetting.setChangeCallback(callback)
   }

}
