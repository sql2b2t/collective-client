package me.collective.client.modules.render

import java.util.function.Predicate
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.ClientAccess
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.render.ColorUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.Camera
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

class CrosshairModule : Module("Crosshair", "Allows you to customize your crosshair.", ModuleCategory.RENDER) {
   private val modeSetting: Setting<CrosshairMode>
   @JvmField
   val thirdPersonSetting: Setting<Boolean>
   @JvmField
   val scaleSetting: Setting<Int>
   private val shadowSetting: Setting<Int>
   private val amplitudeSetting: Setting<Float>
   private val lengthSetting: Setting<Int>
   private val gapSetting: Setting<Int>
   private val widthSetting: Setting<Int>
   private val elementsSetting: Setting<Boolean>
   private val topSetting: Setting<Boolean>
   private val centerSetting: Setting<Boolean>
   private val colorSetting: Setting<Color>
   private val movementAnimation: ClampedInterpolatedFloat

   @EventHandler(priority = -200)
   private fun onRenderHud(event: HudRenderEvent.Unconditional) {
      if (this.modeSetting.getValue() != CrosshairMode.CUSTOM) return
      val debugCrosshairEnabled: Boolean = ClientAccess.collectiveMinecraft.gui.debugOverlay.showDebugScreen()
      if (this.isWorldUnavailable() || ClientAccess.collectiveMinecraft.options.hideGui || debugCrosshairEnabled) {
         return
      }
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera
      if (camera.isDetached()) {
         val thirdPerson = this.thirdPersonSetting
         if (!(thirdPerson.getValue())) {
            return
         }
      }
      val gapSetting = this.gapSetting
      var gap: Float = gapSetting.getValue().toFloat()
      val amplitudeCheck = this.amplitudeSetting
      if (!amplitudeCheck.isAtMinimum()) {
         val targetAnimation = this.movementAnimation
         val movement: Vec2 = ClientAccess.collectiveMinecraft.player!!.input.moveVector
         var moving: Boolean = movement.lengthSquared() > 0.0F
         if (!moving) {
            val keys: Input = ClientAccess.collectiveMinecraft.player!!.input.keyPresses
            moving = keys.jump()
         }
         targetAnimation.setTarget(moving)
         val movementAmplitude: Float = this.amplitudeSetting.getValue()
         val currentAnimation = this.movementAnimation
         gap += movementAmplitude * currentAnimation.updateAndGet()
      }
      val guiScale: Float = ClientAccess.collectiveMinecraft.window.getGuiScale().toFloat()
      val poseStack = event.poseStack!!
      poseStack.pushPose()
      val scaleX: Float = 1.0F / guiScale
      val scaleY: Float = 1.0F / guiScale
      val scaleZ: Float = 1.0F / guiScale
      poseStack.scale(scaleX, scaleY, scaleZ)
      val shadowCheck = this.shadowSetting
      if (!shadowCheck.isAtMinimum()) {
         poseStack.pushPose()
         val shadowX: Float = this.shadowSetting.getValue().toFloat()
         val shadowY: Float = this.shadowSetting.getValue().toFloat()
         poseStack.translate(shadowX, shadowY, 0.0F)
         val shadowLength: Float = this.lengthSetting.getValue().toFloat()
         val shadowWidthValue = this.widthSetting.getValue()
         val shadowWidth: Float = shadowWidthValue.toFloat()
         val shadowHalfWidth: Float = shadowWidth / 2.0F
         val shadowBaseColor: Color = this.colorSetting.getValue()
         val shadowColor: Color = ColorUtils.darken(shadowBaseColor, 0.25F)
         this.drawCrosshair(poseStack, shadowLength, shadowHalfWidth, gap, shadowColor)
         poseStack.popPose()
      }
      val lengthValue = this.lengthSetting.getValue()
      val length: Float = lengthValue.toFloat()
      val widthValue = this.widthSetting.getValue()
      val width: Float = widthValue.toFloat()
      val halfWidth: Float = width / 2.0F
      val color: Color = this.colorSetting.getValue()
      this.drawCrosshair(poseStack, length, halfWidth, gap, color)
      poseStack.popPose()
   }

   private fun drawCrosshair(pose: PoseStack, length: Float, halfWidth: Float, initialGap: Float, color: Color) {
      var gap = initialGap
      val screenWidth: Int = ClientAccess.collectiveMinecraft.window.getScreenWidth()
      var centerX: Int = screenWidth / 2
      val screenHeight: Int = ClientAccess.collectiveMinecraft.window.getScreenHeight()
      var centerY: Int = screenHeight / 2
      val fractionalWidth: Double = halfWidth.toDouble()
      if (Math.ceil(fractionalWidth) - halfWidth != 0.0) {
         gap += 0.5F
      } else {
         centerX--
         centerY++
      }
      val center = this.centerSetting
      if (center.getValue()) {
         GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - halfWidth, centerX + halfWidth, centerY + halfWidth, color)
      }
      if (length != 0.0F) {
         val top = this.topSetting
         if (top.getValue()) {
            GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - gap - length, centerX + halfWidth, centerY - gap, color)
         }
         GuiDrawUtils.fillRect(pose, centerX + gap, centerY - halfWidth, centerX + gap + length, centerY + halfWidth, color)
         GuiDrawUtils.fillRect(pose, centerX - gap - length, centerY - halfWidth, centerX - gap, centerY + halfWidth, color)
         GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY + gap, centerX + halfWidth, centerY + gap + length, color)
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<CrosshairMode>("Mode", CrosshairMode.CUSTOM))
      this.thirdPersonSetting = this.add(BooleanSetting("ThirdPerson", true))
      this.scaleSetting = this.add(NumberSetting<Int>("Scale", 2, 1, 4, Predicate<Int> { ignored -> this.modeSetting.getValue() == CrosshairMode.VANILLA }))
      this.shadowSetting = this.add(NumberSetting<Int>("Shadow", 1, 0, 3, Predicate<Int> { ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM }))
      this.amplitudeSetting = this.add(NumberSetting<Float>("Amplitude", 2.0F, 0.0F, 8.0F, Predicate<Float> { ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM }))
      this.lengthSetting = this.add(NumberSetting<Int>("Length", 2, 0, 16, Predicate<Int> { ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM }))
      val gapVisibility: Predicate<Int> = Predicate { ignored -> !this.lengthSetting.isAtMinimum() }
      this.gapSetting = this.add(NumberSetting<Int>("Gap", 2, 0, 16, gapVisibility))
      this.widthSetting = this.add(NumberSetting<Int>("Width", 1, 1, 5, Predicate<Int> { ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM }))
      this.elementsSetting = this.add(BooleanSetting("Elements", false, Predicate<Boolean> { ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM }).markAsGroup().markVisibilityDependency())
      this.topSetting = this.add(BooleanSetting("Top", true).dependsOn(this.elementsSetting))
      this.centerSetting = this.add(BooleanSetting("Center", true).dependsOn(this.elementsSetting))
      this.colorSetting = this.add(ColorSetting("Color", Color(-2828, true)).dependsOn(this.elementsSetting))

      this.movementAnimation = ClampedInterpolatedFloat(
         2.0F, true
      )
      val amplitudeLabel = this.amplitudeSetting
      val minimumLabel = SettingLabelCondition.MIN
      amplitudeLabel.setBoundaryLabel("None", minimumLabel)
      this.shadowSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.lengthSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.setDrawn(false)
   }

   override fun fromJson(json: JsonElement) {
      val objectJson: JsonObject = json.getAsJsonObject()
      val settings: JsonObject = objectJson.getAsJsonObject("settings")
      if (settings != null && !settings.has("Mode") && settings.has("Vanilla")) {
         val migrated: JsonObject = objectJson.deepCopy()
         migrated.getAsJsonObject("settings").addProperty("Mode", if (settings.get("Vanilla").getAsBoolean()) "Vanilla" else "Custom")
         super.fromJson(migrated)
      } else {
         super.fromJson(json)
      }
   }

   enum class CrosshairMode(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla"),
      CUSTOM("Custom");

      override fun getName(): String {
         return this.displayName
      }
   }

   fun shouldHideVanillaCrosshair(): Boolean {
      return this.isToggled() && this.modeSetting.getValue() == CrosshairMode.CUSTOM
   }
}
