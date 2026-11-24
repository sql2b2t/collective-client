package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.PacketEvent
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.network.InteractPacketUtils
import me.collective.client.audio.BuiltinSound
import me.collective.client.render.ColorUtils
import me.collective.client.managers.SoundManager
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.VertexGeometry
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.collective.mixin.ServerboundInteractPacketAccessor
import java.awt.Color
import java.util.function.Predicate
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

class HitmarkerModule : Module("Hitmarker", "Marks your screen when you hit entities.", ModuleCategory.RENDER) {
   private val drawSetting: Setting<Boolean>
   private val lengthSetting: Setting<Float>
   private val timeSetting: Setting<Float>
   private val fadeTimeSetting: Setting<Float>
   private val colorSetting: Setting<Color>
   private val soundSetting: Setting<Boolean>
   private val soundTypeSetting: Setting<NamedIdentifier>
   private val volumeSetting: Setting<Float>
   private val targetsSetting: Setting<Boolean>
   private val animalsSetting: Setting<Boolean>
   private val hostilesSetting: Setting<Boolean>
   private val playersSetting: Setting<Boolean>
   private val crystalsSetting: Setting<Boolean>
   @JvmField
   var lastHitTime: Long
   @JvmField
   var soundPending: Boolean

   fun isEligibleTarget(entity: Entity): Boolean {
      val animalsEnabled = this.animalsSetting.getValue()
      if (animalsEnabled && entity is AgeableMob) {
         return true
      }
      val hostilesEnabled = this.hostilesSetting.getValue()
      if (hostilesEnabled && entity is Enemy) {
         return true
      }
      val playersEnabled = this.playersSetting.getValue()
      if (playersEnabled && entity is Player) {
         return true
      }
      return this.crystalsSetting.getValue() && entity is EndCrystal
   }

   private fun getMarkerOpacity(): Float {
      var secondsCapture = this.timeSetting.getValue()

      val durationMillis = (secondsCapture * 1e+03f).toLong()
      secondsCapture = this.fadeTimeSetting.getValue()

      val fadeMillis = (secondsCapture * 1e+03f).toLong()
      val elapsedMillis = System.currentTimeMillis() - this.lastHitTime
      if (elapsedMillis < durationMillis - fadeMillis) {

         return 1.0f
      } else {

         return 1.0f - (elapsedMillis - (durationMillis - fadeMillis)).toFloat() / fadeMillis.toFloat()
      }
   }

   override fun onToggle() {

      this.lastHitTime = -1L
      this.soundPending = false
   }

   @EventHandler
   private fun onHudRender(event: HudRenderEvent) {
      if (this.isWorldUnavailable()) {
         return
      }
      if (this.soundPending) {
         this.soundPending = false
         val soundEnabled = this.soundSetting.getValue()
         if (soundEnabled) {
            val soundManagerCapture: SoundManager = ClientContext.soundManager
            val soundIdentifierCapture = this.soundTypeSetting.getValue()
            val volume = this.volumeSetting.getValue()
            soundManagerCapture.playSound(soundIdentifierCapture, volume)
         }
      }
      if (ClientAccess.collectiveMinecraft.options.hideGui) {
         return
      }
      val gameRenderer: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val camera: Camera = gameRenderer.mainCamera
      if (camera.isDetached()) {
         return
      }
      val hitTimeCapture = this.lastHitTime

      if (hitTimeCapture == -1L) {
         return
      }

      val elapsedMillis = System.currentTimeMillis()
         - this.lastHitTime
      val durationCapture = this.timeSetting.getValue()

      val durationMillis = (durationCapture * 1e+03f).toLong()
      if (elapsedMillis > durationMillis) {

         this.lastHitTime = -1L
         return
      }

      val opacity = this.getMarkerOpacity()
      if (!(opacity > 0.0f)) {
         return
      }
      val drawEnabled = this.drawSetting.getValue()
      if (!drawEnabled) {
         return
      }
      val scaledWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat()

      var centerX = scaledWidth / 2.0f
      val scaledHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat()

      val halfHeight = scaledHeight / 2.0f

      var centerY = halfHeight - 0.5f
      val crosshairForOffset = crosshair!!
      if (!crosshairForOffset.isToggled()) {

         centerX -= 0.5f
      } else {

         centerY += 0.5f
      }

      val lengthValue = this.lengthSetting.getValue()
      val outerOffset = 2.0f + lengthValue
      val colorOption: Setting<Color> = this.colorSetting
      val markerColor = ColorUtils.multiplyAlpha(
         colorOption.getValue(), opacity
      )
      val buffer: VertexConsumer = GuiDrawUtils.getLineVertices()
      val poseStackForPush: PoseStack = event.poseStack!!
      poseStackForPush.pushPose()
      val crosshairForTranslation = crosshair!!
      if (crosshairForTranslation.isToggled()) {
         val translationStackCapture: PoseStack = event.poseStack!!

         translationStackCapture.translate(-0.6, 0.0, 0.0)
      }

      val topLeftStackCapture: PoseStack = event.poseStack!!
      val topLeftOuterX = centerX - outerOffset
      var lineYCapture = centerY - outerOffset

      var lineXCapture = centerX - 2.0f

      val topLeftEndY = centerY - 2.0f
      drawMarkerLine(
         topLeftStackCapture, buffer, markerColor, topLeftOuterX, lineYCapture, lineXCapture, topLeftEndY
      )
      val bottomLeftStackCapture: PoseStack = event.poseStack!!
      val bottomLeftOuterX = centerX - outerOffset
      lineYCapture = centerY + outerOffset

      lineXCapture = centerX - 2.0f

      val bottomLeftEndY = centerY + 2.0f
      drawMarkerLine(
         bottomLeftStackCapture, buffer, markerColor, bottomLeftOuterX, lineYCapture, lineXCapture, bottomLeftEndY
      )
      val topRightStackCapture: PoseStack = event.poseStack!!

      val topRightInnerX = centerX + 2.0f

      lineYCapture = centerY - 2.0f
      lineXCapture = centerX + outerOffset
      val topRightEndY = centerY - outerOffset
      drawMarkerLine(
         topRightStackCapture, buffer, markerColor, topRightInnerX, lineYCapture, lineXCapture, topRightEndY
      )
      val bottomRightStackCapture: PoseStack = event.poseStack!!

      val bottomRightInnerX = centerX + 2.0f

      lineYCapture = centerY + 2.0f
      lineXCapture = centerX + outerOffset
      val bottomRightEndY = centerY + outerOffset
      drawMarkerLine(
         bottomRightStackCapture, buffer, markerColor, bottomRightInnerX, lineYCapture, lineXCapture, bottomRightEndY
      )
      val poseStackForPop: PoseStack = event.poseStack!!
      poseStackForPop.popPose()

   }

   @EventHandler(priority = -100)
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (this.isWorldUnavailable()) {
         return
      }
      val packet = event.packet as? ServerboundInteractPacket ?: return
      if (InteractPacketUtils.getAction(packet) != InteractPacketUtils.Action.ATTACK) {
         return
      }
      val levelCapture: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val entityId = (packet as ServerboundInteractPacketAccessor).entityId
      val target = levelCapture.getEntity(entityId)
      val elapsedMillis = System.currentTimeMillis()
         - this.lastHitTime

      if (elapsedMillis <= 100L) {
         return
      }

      val simulatedPlayer = InteractPacketUtils.getEntity(packet) is FakeplayerCommand.SimulatedPlayer
      if (event.isCancelled() && !simulatedPlayer) {
         return
      }

      if (target == null) {
         return
      }
      if (target.level() == null || target === ClientAccess.collectiveMinecraft.player) {
         return
      }
      val targetLevel: Level = target.level()
      if (!targetLevel.isClientSide()) {
         return
      }
      if (this.isEligibleTarget(target)) {
         this.lastHitTime = System.currentTimeMillis()
         this.soundPending = true
      }
   }

   init {
      this.drawSetting = this.add(BooleanSetting("Draw", true).markVisibilityDependency())
      val lengthVisibility = Predicate<Any> { ignored -> this.drawSetting.isExpanded() }
      this.lengthSetting = this.add(NumberSetting<Float>("Length", 3.0f, 0.1f, 10.0f, lengthVisibility))
      val timeVisibility = Predicate<Any> { ignored -> this.drawSetting.isExpanded() }
      this.timeSetting = this.add(NumberSetting<Float>("Time", 1.0f, 0.5f, 3.0f, timeVisibility).withUnit("s"))
      val fadeTimeVisibility = Predicate<Any> { ignored -> this.drawSetting.isExpanded() }
      this.fadeTimeSetting = this.add(NumberSetting<Float>("FadeTime", 0.5f, 0.5f, 3.0f, fadeTimeVisibility).withUnit("s"))
      val colorVisibility = Predicate<Any> { ignored -> this.drawSetting.isExpanded() }
      this.colorSetting = this.add(ColorSetting("Color", Color(-1581409793, true), colorVisibility))
      this.soundSetting = this.add(BooleanSetting("Sound", false).markVisibilityDependency())
      this.soundTypeSetting = this.add(IdentifierSetting("Sound", BuiltinSound.NEVERLOSE.identifier).dependsOn(this.soundSetting))
      val volumeVisibility = Predicate<Any> { ignored -> this.soundSetting.isExpanded() }
      this.volumeSetting = this.add(NumberSetting<Float>("Volume", 0.5f, 0.0f, 1.0f, volumeVisibility))
      this.targetsSetting = this.add(BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency())
      val animalsVisibility = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.animalsSetting = this.add(BooleanSetting("Animals", false, animalsVisibility))
      val hostilesVisibility = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.hostilesSetting = this.add(BooleanSetting("Hostiles", false, hostilesVisibility))
      val playersVisibility = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.playersSetting = this.add(BooleanSetting("Players", true, playersVisibility))
      val crystalsVisibility = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.crystalsSetting = this.add(BooleanSetting("Crystals", false, crystalsVisibility))

      this.lastHitTime = -1L
      this.soundPending = false

      this.setDrawn(false)
      this.soundTypeSetting.setConfigName("Type")
   }

   companion object {
      private var crosshair: CrosshairModule? = null

      private fun drawMarkerLine(
         poseStack: PoseStack, buffer: VertexConsumer, color: Color, startX: Float, startY: Float, endX: Float, endY: Float
      ) {
         VertexGeometry.appendLine(buffer, poseStack.last(), startX, startY, 0.0f, endX, endY, 0.0f, 1.0f, color.hashCode())
      }

      @JvmStatic
      fun bindCrosshair(registry: ModuleRegistry) {
         crosshair = registry.getModule(
            CrosshairModule::class.java
         ) as CrosshairModule
      }
   }

}
