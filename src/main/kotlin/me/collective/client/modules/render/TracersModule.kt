package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ColorsSchemeChoice
import me.collective.client.settings.options.TracersHitboxChoice
import me.collective.client.settings.options.TracersModeChoice

import java.awt.Color
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

class TracersModule : Module(
   "Tracers",
   "Draws lines from your crosshair to the players nearby.",
   ModuleCategory.RENDER
) {

   internal val modeSetting: Setting<TracersModeChoice> = add(EnumSetting("Mode", TracersModeChoice.LINES))

   @JvmField
   val hitboxSetting: Setting<TracersHitboxChoice> = add(EnumSetting("Hitbox", TracersHitboxChoice.HEAD) { ignored -> this.modeSetting.value == TracersModeChoice.LINES })

   private val minDistanceSetting: Setting<Float> = add(NumberSetting("MinDistance", 0.0F, 0.0F, 128.0F).withUnit("m"))

   private val maxDistanceSetting: Setting<Float> = add(NumberSetting("MaxDistance", 256.0F, 8.0F, 256.0F).withUnit("m"))

   @JvmField
   val shadowSetting: Setting<Int> = add(NumberSetting("Shadow", 1, 0, 3) { ignored -> this.modeSetting.value == TracersModeChoice.MARKER })

   @JvmField
   val distanceSetting: Setting<Float> = add(NumberSetting("Distance", 16.0F, 8.0F, 50.0F) { ignored -> this.modeSetting.value == TracersModeChoice.MARKER }.withUnit("px"))

   @JvmField
   val lineWidthSetting: Setting<Float> = add(NumberSetting("LineWidth", 1.0F, 0.1F, 5.0F) { ignored -> this.modeSetting.value == TracersModeChoice.LINES })

   @JvmField
   val stemSetting: Setting<Boolean> = add(BooleanSetting("Stem", true) { ignored -> this.modeSetting.value == TracersModeChoice.LINES })

   private val colorsSetting: Setting<Boolean> = add(BooleanSetting("Colors", false).markAsGroup())

   private val colorSetting: Setting<Color> = add(ColorSetting("Color", Color(-65536, true)).dependsOn(this.colorsSetting))

   private val distanceColorSetting: Setting<Boolean> = add(BooleanSetting("DistanceColor", false).dependsOn(this.colorsSetting))

   private val friendColorSetting: Setting<Boolean> = add(BooleanSetting("FriendColor", true).dependsOn(this.colorsSetting))

   private val enemyColorSetting: Setting<Boolean> = add(BooleanSetting("EnemyColor", true).dependsOn(this.colorsSetting))

   private val targetsSetting: Setting<Boolean> = add(BooleanSetting("Targets", false).markAsGroup())

   @JvmField
   val ignoreVisibleSetting: Setting<Boolean> = add(BooleanSetting("IgnoreVisible", true) { ignored -> this.modeSetting.value == TracersModeChoice.MARKER }.dependsOn(this.targetsSetting))

   private val ignoreFriendsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreFriends", false).dependsOn(this.targetsSetting))

   private val ignoreNakedsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetsSetting))

   private val lineRenderer: LineTracerRenderer = LineTracerRenderer(this)

   private val markerRenderer: MarkerTracerRenderer = MarkerTracerRenderer(this)

   fun shouldSkip(player: Player): Boolean {
      val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val capturedCameraPosition = gameRenderer.mainCamera.position()
      val playerPosition = player.position()
      val distance = capturedCameraPosition.distanceTo(playerPosition)
      if (player === ClientAccess.collectiveMinecraft.player!!) {
         return true
      }
      val maximum = this.maxDistanceSetting.value!!
      if (distance > maximum) {
         return true
      }
      val minimum = this.minDistanceSetting.value!!
      if (distance < minimum) {
         return true
      }
      if (this.ignoreFriendsSetting.value!!) {
         val friendRegistry = ClientContext.socialRegistry!!
         if (friendRegistry.isFriendPlayer(player)) {
            return true
         }
      }
      if (this.ignoreNakedsSetting.value!! && !PlayerStateUtils.hasArmor(player)) {
         return true
      }
      return false
   }

   fun getEndpoints(player: Player): TracerEndpoints {
      val tickDelta = RenderUtils.getPartialTick()
      val position = player.getPosition(tickDelta)
      val bounds = player.boundingBox
      if (player.isPassenger) {
         return TracerEndpoints.riding(player, position)
      }
      if (player.isSleeping) {
         return TracerEndpoints.sleeping(player)
      }
      val height = bounds.ysize
      if (height <= 1.0) {
         return TracerEndpoints.lowPose(player, position, bounds)
      }
      return TracerEndpoints.standing(player, position)
   }

   @EventHandler
   private fun onRenderMarkers(event: HudRenderEvent) {
      val mode = this.modeSetting
      if (mode.value == TracersModeChoice.MARKER) {
         val markerRenderer = this.markerRenderer
         val eventPoseStack = event.poseStack
         val tickDelta = event.partialTick
         markerRenderer.render(eventPoseStack!!, tickDelta)
      }
   }

   @EventHandler(priority = -9999)
   private fun onRenderLines(event: WorldRenderEvent.Main) {
      val mode = this.modeSetting
      if (mode.value == TracersModeChoice.LINES) {
         val lineRenderer = this.lineRenderer
         val eventPoseStack = event.poseStack
         val tickDelta = event.partialTick
         lineRenderer.render(eventPoseStack!!, tickDelta)
      }
   }

   fun getColor(player: Player, position: Vec3): Color {
      var color = this.colorSetting.value!!
      val distanceColor = this.distanceColorSetting.value!!
      if (distanceColor) {
         val distance = ClientAccess.collectiveMinecraft.player!!.eyePosition.distanceTo(position)

         val clampedDistance = Mth.clamp(distance, 8.0, 64.0)

         val distanceOffset = clampedDistance - 8.0

         val distanceFraction = 1.0F - (distanceOffset / 56.0).toFloat()
         val distanceGradient = ColorUtils.blend(
            Color.red, Color.green, distanceFraction
         )
         val alphaColor = this.colorSetting.value!!
         color = ColorUtils.withAlpha(distanceGradient, alphaColor.alpha)
      }

      val friendRegistry = ClientContext.socialRegistry!!
      if (friendRegistry.isFriendPlayer(player) && this.friendColorSetting.value!!) {
         color = ColorUtils.withAlpha(
            friendRegistry.friendColor, this.colorSetting.value!!.alpha
         )
      } else if (friendRegistry.isEnemyPlayer(player) && this.enemyColorSetting.value!!) {
         color = ColorUtils.withAlpha(
            friendRegistry.enemyColor, this.colorSetting.value!!.alpha
         )
      }

      if (distanceColor) {
         val schemeSetting: Setting<ColorsSchemeChoice> = colorsModule!!.schemeSetting
         val configuredScheme = schemeSetting.value!!
         color = configuredScheme.transform(color)
      }

      return color
   }

   init {
      this.minDistanceSetting.setChangeCallback {
         val maximum = this.maxDistanceSetting.value!!
         val minimum = this.minDistanceSetting.value!!
         this.maxDistanceSetting.value = maxOf(maximum, minimum)
      }
      this.maxDistanceSetting.setChangeCallback {
         val maximum = this.maxDistanceSetting.value!!
         val minimum = this.minDistanceSetting.value!!
         // min прижимается к max снизу: раньше стоял maxOf и поднятие MaxDistance
         // поднимало MinDistance до максимума (диапазон схлопывался, трассеры пропадали).
         this.minDistanceSetting.value = minOf(minimum, maximum)
      }
   }

   companion object {
      private var colorsModule: ColorsModule? = null

      @JvmStatic
      fun bindReferencedModules(moduleRegistry: ModuleRegistry) {
         colorsModule = moduleRegistry.getModule(ColorsModule::class.java) as ColorsModule?
      }
   }
}
