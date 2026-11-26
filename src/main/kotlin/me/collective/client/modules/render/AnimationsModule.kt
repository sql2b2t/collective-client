package me.collective.client.modules.render

import net.minecraft.world.entity.EntityTypes
import me.collective.client.ClientAccess
import me.collective.client.events.CameraSetupEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import net.minecraft.client.renderer.entity.EndCrystalRenderer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.phys.Vec3

class AnimationsModule : Module("Animations", "Modifies entity animations.", ModuleCategory.RENDER) {
   private val smoothCameraSetting: Setting<Boolean> = add(BooleanSetting("SmoothCamera", false))

   @JvmField
   val crystalsSetting: Setting<Boolean> = add(BooleanSetting("Crystals", false).markVisibilityDependency())

   @JvmField
   val floatFactorSetting: Setting<Float> = add(NumberSetting("FloatFactor", 1.0F, 0.0F, 1.0F).dependsOn(this.crystalsSetting))

   @JvmField
   val rotationSpeedSetting: Setting<Float> = add(NumberSetting("RotationSpeed", 1.0F, 0.0F, 6.0F).dependsOn(this.crystalsSetting))

   @JvmField
   val crystalScaleSetting: Setting<Float> = add(NumberSetting("CrystalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.crystalsSetting))

   private val partsSetting: Setting<Boolean> = add(BooleanSetting("Parts", false).markAsGroup().markVisibilityDependency().dependsOn(this.crystalsSetting))

   private val innerSetting: Setting<Boolean> = add(BooleanSetting("Inner", true).dependsOn(this.crystalsSetting, this.partsSetting))

   private val outerSetting: Setting<Boolean> = add(BooleanSetting("Outer", true).dependsOn(this.crystalsSetting, this.partsSetting))

   private val coreSetting: Setting<Boolean> = add(BooleanSetting("Core", true).dependsOn(this.crystalsSetting, this.partsSetting))

   private val bottomSetting: Setting<Boolean> = add(BooleanSetting("Bottom", true).dependsOn(this.crystalsSetting, this.partsSetting))

   @JvmField
   val playersSetting: Setting<Boolean> = add(BooleanSetting("Players", false).markVisibilityDependency())

   @JvmField
   val staticSetting: Setting<Boolean> = add(BooleanSetting("Static", false).dependsOn(this.playersSetting))

   @JvmField
   val sneakSetting: Setting<Boolean> = add(BooleanSetting("Sneak", false).dependsOn(this.playersSetting))

   @JvmField
   val playerScaleSetting: Setting<Float> = add(NumberSetting("PlayerScale", 1.0F, 0.0F, 2.0F).dependsOn(this.playersSetting))

   private var verticalCameraCatchUp: Boolean = false

   private var horizontalCameraCatchUp: Boolean = false

   private var cameraPosition: Vec3? = null

   private var previousCameraPosition: Vec3? = null

   fun shouldHideCrystalPart(partIndex: Int): Boolean {
      if (!this.isToggled()) {
         return false
      }
      return when (partIndex) {
         0 -> this.shouldHideCrystalBottom()
         1 -> this.shouldHideCrystalOuter()
         2 -> this.shouldHideCrystalInner()
         3 -> this.shouldHideCrystalCore()
         else -> false
      }
   }

   fun isPlayerScalingEnabled(): Boolean {
      if (this.isToggled()) {
         if (this.playersSetting.value!!) {
            val scale = this.playerScaleSetting.value!!

            if (scale != 1.0F) {
               return true
            }
         }
      }

      return false
   }

   fun resetCrystalModelVisibility() {
      val resetLevel = ClientAccess.collectiveMinecraft.level ?: return
      val crystal = EndCrystal(
         EntityTypes.END_CRYSTAL, resetLevel
      )
      val dispatcherSnapshot = ClientAccess.collectiveMinecraft.entityRenderDispatcher
      val renderer = dispatcherSnapshot.getRenderer(crystal) as EndCrystalRenderer
      renderer.model.base.skipDraw = false
      renderer.model.cube.skipDraw = false
      renderer.model.innerGlass.skipDraw = false
      renderer.model.outerGlass.skipDraw = false
   }

   fun shouldHideCrystalBottom(): Boolean {
      return this.isCrystalAnimationEnabled() && !this.bottomSetting.value!!
   }

   fun shouldHideCrystalOuter(): Boolean {
      return this.isCrystalAnimationEnabled() && !this.outerSetting.value!!
   }

   fun isStaticPlayerAnimationEnabled(): Boolean {
      return this.isToggled() && this.playersSetting.value!! && this.staticSetting.value!!
   }

   fun isSneakingAnimationEnabled(): Boolean {
      return this.isToggled() && this.playersSetting.value!! && this.sneakSetting.value!!
   }

   fun shouldHideCrystalCore(): Boolean {
      return this.isCrystalAnimationEnabled() && !this.coreSetting.value!!
   }

   fun isCrystalAnimationEnabled(): Boolean {
      return this.isToggled() && this.crystalsSetting.value!!
   }

   fun shouldHideCrystalInner(): Boolean {
      return this.isCrystalAnimationEnabled() && !this.innerSetting.value!!
   }

   override fun onDisable() {
      this.resetCrystalModelVisibility()
   }

   private fun updateCameraCatchUp(previousCatchUp: Boolean, distance: Double, catchUpThreshold: Double): Boolean {
      if (distance > catchUpThreshold || PlayerStateUtils.isFallFlying()) {
         return true
      }
      return if (distance < 0.1) false else previousCatchUp
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val player = ClientAccess.collectiveMinecraft.player!!
      val playerPosition = player.position()
      if (this.smoothCameraSetting.value!!
         && this.cameraPosition != null &&
         this.previousCameraPosition != null) {
         val cameraPositionSnapshot = this.cameraPosition!!
         this.previousCameraPosition = this.cameraPosition
         val horizontalDistance: Double = Vec3Utils.horizontalDistance(cameraPositionSnapshot, playerPosition).toDouble()
         val verticalDelta = playerPosition.y - cameraPositionSnapshot.y
         val verticalDistance = Math.abs(verticalDelta)
         val previousHorizontalCatchUp = this.horizontalCameraCatchUp
         this.horizontalCameraCatchUp = this.updateCameraCatchUp(previousHorizontalCatchUp, horizontalDistance, 3.0)
         val previousVerticalCatchUp = this.verticalCameraCatchUp
         this.verticalCameraCatchUp = this.updateCameraCatchUp(previousVerticalCatchUp, verticalDistance, 3.5)

         var horizontalFraction = minOf(1.0, horizontalDistance) / horizontalDistance
         if (horizontalFraction.isNaN() || horizontalDistance > 64.0) {
            horizontalFraction = 1.0
         } else if (this.horizontalCameraCatchUp) {
            horizontalFraction = minOf(5.0, horizontalDistance) / horizontalDistance
         }

         var verticalFraction = minOf(0.7, verticalDistance) / verticalDistance
         if (verticalFraction.isNaN() || this.verticalCameraCatchUp) {
            verticalFraction = 1.0
         }

         this.cameraPosition = Vec3(
            Mth.lerp(horizontalFraction, cameraPositionSnapshot.x, playerPosition.x),
            Mth.lerp(verticalFraction, cameraPositionSnapshot.y, playerPosition.y),
            Mth.lerp(horizontalFraction, cameraPositionSnapshot.z, playerPosition.z)
         )
      } else {
         this.cameraPosition = playerPosition
         this.previousCameraPosition = playerPosition
      }
   }

   @EventHandler(priority = 100)
   private fun onCameraSetupEvent(event: CameraSetupEvent) {
      if (!this.smoothCameraSetting.value!!) {
         return
      }
      val options = ClientAccess.collectiveMinecraft.options
      val cameraType = options.cameraType
      if (!cameraType.isFirstPerson || this.cameraPosition == null || this.previousCameraPosition == null) {
         return
      }
      val player = ClientAccess.collectiveMinecraft.player!!
      if (player.abilities.flying) {
         return
      }
      val partialTick = event.partialTick
      val currentPosition = this.cameraPosition!!
      val interpolatedPosition = this.previousCameraPosition!!.lerp(currentPosition, partialTick.toDouble())
      val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val camera = gameRenderer.mainCamera()
      val currentEyeHeight = camera.eyeHeight
      val previousEyeHeight = camera.eyeHeightOld
      val eyeHeight = Mth.lerp(partialTick, previousEyeHeight, currentEyeHeight)
      val cameraPosition = interpolatedPosition.add(0.0, eyeHeight.toDouble(), 0.0)
      event.setPosition(cameraPosition)
      event.cancel()
   }

   init {
      this.crystalsSetting.setChangeCallback { this.resetCrystalModelVisibility() }
   }

}
