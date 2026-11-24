package me.collective.client.modules.render

import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.LineMeshRenderer
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.ArrayList
import java.util.HashMap
import java.util.Map.Entry
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.phys.Vec3

class TrailsModule : Module("Trails", "Draws trails behind certain entities.", ModuleCategory.RENDER) {
   private val trailsByEntityId: HashMap<Int, MutableList<TrailPoint>> = HashMap()
   private val maxTrailPoints: Int = 2048
   private val renderSetting: Setting<Boolean>
   private val bloomSetting: Setting<Boolean>
   private val delaySetting: Setting<Float>
   private val lineWidthSetting: Setting<Float>
   private val fadeSetting: Setting<Boolean>
   private val fadeDelaySetting: Setting<Float>
   private val fadeDurationSetting: Setting<Float>
   private val targetsSetting: Setting<Boolean>
   private val selfSetting: Setting<Boolean>
   private val playersSetting: Setting<Boolean>
   private val pearlsSetting: Setting<Boolean>
   private val arrowsSetting: Setting<Boolean>
   private val expSetting: Setting<Boolean>
   private val colorSetting: Setting<Color>
   private val thresholdSetting: Setting<Double>
   private val sampleTimer: ElapsedTimer

   private fun getFadeDelayMillis(): Long {
      val delayValue = this.fadeDelaySetting.getValue()

      return (delayValue * 1e+03f).toLong()
   }

   override fun onToggle() {
      this.trailsByEntityId.clear()
   }

   @EventHandler(priority = -100)
   private fun onBeforeBufferSource(event: WorldRenderEvent.BeforeBufferSource) {
      if (!this.isWorldUnavailable()) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (player.isDeadOrDying) {
            this.trailsByEntityId.clear()
         } else {
            val delaySeconds = this.delaySetting.getValue()
            if (delaySeconds != 0.0f) {
               val timerBeforeCheck = this.sampleTimer
               val delayValue: Double = this.delaySetting.getValue().toFloat().toDouble()
               val delayUnit = TimeUnit.SECONDS
               if (!timerBeforeCheck.hasElapsed(delayValue, delayUnit)) {
                  return
               }
            }

            this.sampleTimer.reset()
            val playerTick = ClientAccess.collectiveMinecraft.player!!.tickCount

            if (playerTick % 4 == 0) {
               var emptyEntityId = 0
               for (entityId in this.trailsByEntityId.keys) {
                  val points = this.trailsByEntityId[entityId]!!
                  points.removeIf { expiringPoint ->
                     val fadeEnabled = this.fadeSetting.getValue()
                     if (fadeEnabled) {
                        val pointAgeMillis = System.currentTimeMillis()
                           - expiringPoint.createdAtMillis

                        val fadeDelayMillis = this.getFadeDelayMillis()

                        if (pointAgeMillis
                           > fadeDelayMillis + this.getFadeDurationMillis()
                        ) {
                           return@removeIf true
                        }
                     }

                     false
                  }
                  if (!this.fadeSetting.getValue()) {
                     // Без Fade (дефолт) точки раньше не удалялись вовсе: трейл рос каждый кадр
                     // бесконечно (утечка памяти + деградация FPS). Ограничиваем длину трейла.
                     val excess = points.size - this.maxTrailPoints
                     if (excess > 0) {
                        repeat(excess) { points.removeAt(0) }
                     }
                  }
                  if (points.isEmpty()) {
                     emptyEntityId = entityId
                     break
                  }
               }

               if (emptyEntityId != 0) {
                  this.trailsByEntityId.remove(emptyEntityId)
               }
            }

            val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
            for (entity in level.entitiesForRendering()) {

               if (this.shouldTrackEntity(entity)) {
                  val point = TrailPoint(entity.getPosition(RenderUtils.getPartialTick()))
                  val trailMap = this.trailsByEntityId
                  val newEntityId = entity.id
                  val entityTrail = trailMap.computeIfAbsent(newEntityId) { entityId -> ArrayList() }
                  entityTrail.add(point)
               }
            }
         }
      }
   }

   private fun renderTrail(poseStack: PoseStack, points: List<TrailPoint>, lineWidth: Float) {
      LineMeshRenderer.begin(poseStack, lineWidth)
      val redValue = this.colorSetting.getValue().red
      val greenValue = this.colorSetting.getValue().green
      val blueValue = this.colorSetting.getValue().blue
      val baseHsb: FloatArray = Color.RGBtoHSB(redValue, greenValue, blueValue, null)
      for (pointIndex in 1 until points.size) {
         val previous = points[pointIndex - 1]
         val point = points[pointIndex]
         LineMeshRenderer.addVertex(
            previous.position.x, previous.position.y, previous.position.z,
            this.getPointColor(baseHsb, previous).hashCode(), true, false
         )
         LineMeshRenderer.addVertex(
            point.position.x, point.position.y, point.position.z,
            this.getPointColor(baseHsb, point).hashCode(), true, false
         )
      }
      LineMeshRenderer.draw()
   }

   private fun getPointColor(baseHsb: FloatArray, point: TrailPoint): Color {
      val pointCreatedAtMillis = point.createdAtMillis.toDouble()
      val thresholdFactor = this.thresholdSetting.getValue()

      val huePeriod = pointCreatedAtMillis / (thresholdFactor * 20.0)
      val hue = (
         Math.ceil(huePeriod) %
         MathConstants.FULL_TURN_DEGREES /
         MathConstants.FULL_TURN_DEGREES
         ).toFloat()
      val nowMillis = System.currentTimeMillis()
      val pointAgeMillis = nowMillis - point.createdAtMillis

      val ageAfterFadeDelay = (pointAgeMillis - this.getFadeDelayMillis()).toFloat()

      val fadeFraction = 1.0f - ageAfterFadeDelay / this.getFadeDurationMillis().toFloat()
      val alphaColor = this.colorSetting.getValue()
      val rawAlpha = fadeFraction * alphaColor.alpha

      val clampedAlpha = Mth.clamp(rawAlpha, 0.0f, 255.0f)

      var alphaMultiplier = clampedAlpha / 255.0f
      val colorSettingForMode = this.colorSetting as ColorSetting

      val selectedColor: Color
      if (colorSettingForMode.isRainbow) {
         val baseSaturation = baseHsb[1]

         val brightness = baseHsb[2]
         selectedColor = Color.getHSBColor(hue, baseSaturation, brightness)
      } else {
         selectedColor = this.colorSetting.getValue()
      }

      val color = selectedColor
      val fadeEnabled = this.fadeSetting.getValue()
      if (fadeEnabled) {
         val nowForFadeStart = System.currentTimeMillis()
         val pointAgeAtFadeStart = nowForFadeStart - point.createdAtMillis

         if (pointAgeAtFadeStart >= this.getFadeDelayMillis()) {
            return ColorUtils.multiplyAlpha(color, alphaMultiplier)
         }
      }

      alphaMultiplier = 1.0f
      return ColorUtils.multiplyAlpha(color, 1.0f)
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      if (!this.isWorldUnavailable()) {
         val renderEnabled = this.renderSetting.getValue()
         if (renderEnabled) {
            val mainTarget: RenderTarget = ClientAccess.collectiveMinecraft.mainRenderTarget
            val maskTarget = MaskShaderRenderer.MASK_TARGET
            val maskColor: Setting<Color> = this.colorSetting
            RenderUtils.clearColor(
               maskTarget, maskColor.getValue()
            )
            RenderUtils.clearDepth(
               MaskShaderRenderer.MASK_TARGET
            )
            RenderTargetUtils.setMainRenderTarget(
               MaskShaderRenderer.MASK_TARGET
            )
            for (entry in this.trailsByEntityId.entries) {
               val pointCount = entry.value.size

               if (pointCount > 2) {
                  val poseStack: PoseStack = event.poseStack!!
                  val points = entry.value
                  val width = this.lineWidthSetting.getValue()
                  this.renderTrail(poseStack, points, width)
               }
            }

            RenderTargetUtils.setMainRenderTarget(mainTarget)
            RenderUtils.blitToMain(
               MaskShaderRenderer.MASK_TARGET
            )
            val bloomEnabled = this.bloomSetting.getValue()
            if (!bloomEnabled) {
               return
            }

            val bloomWidth = this.lineWidthSetting.getValue()
            val effectWidth = 6.0f + bloomWidth

            MaskShaderRenderer.setParameters(
               effectWidth, 1.5f, 1.0f, 2.0f
            )

            MaskShaderRenderer.renderEffect("bloom_mask")
            return
         }
      }
   }

   private fun shouldTrackEntity(entity: Entity): Boolean {
      if (entity === ClientAccess.collectiveMinecraft.player!!) {
         val includeSelf = this.selfSetting.getValue()
         if (includeSelf) {
            return true
         }
      }

      if (entity is Player) {
         val includePlayers = this.playersSetting.getValue()
         if (includePlayers && entity !== ClientAccess.collectiveMinecraft.player!!) {
            return true
         }
      }

      if (entity is ThrownEnderpearl) {
         val includePearls = this.pearlsSetting.getValue()
         if (includePearls) {
            return true
         }
      }

      if (entity is ThrownExperienceBottle) {
         val includeExperienceBottles = this.expSetting.getValue()
         if (includeExperienceBottles) {
            return true
         }
      }

      if (entity is Arrow) {
         val includeArrows = this.arrowsSetting.getValue()
         if (includeArrows) {
            return true
         }
      }

      return false
   }

   private fun getFadeDurationMillis(): Long {
      val durationValue = this.fadeDurationSetting.getValue()

      return (durationValue * 1e+03f).toLong()
   }

   init {
      this.renderSetting = this.add(BooleanSetting("Render", true))
      this.bloomSetting = this.add(BooleanSetting("Bloom", false))
      this.delaySetting = this.add(NumberSetting<Float>("Delay", 0.0f, 0.0f, 5.0f).withUnit("s"))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.1f, 5.0f))
      this.fadeSetting = this.add(BooleanSetting("Fade", false).markVisibilityDependency())
      val fadeDelayVisible = Predicate<Any> { ignored -> this.fadeSetting.isExpanded() }
      this.fadeDelaySetting = this.add(NumberSetting<Float>("FadeDelay", 1.0f, 0.0f, 10.0f, fadeDelayVisible).withUnit("s"))
      val fadeDurationVisible = Predicate<Any> { ignored -> this.fadeSetting.isExpanded() }
      this.fadeDurationSetting = this.add(NumberSetting<Float>("FadeDuration", 0.5f, 0.0f, 10.0f, fadeDurationVisible).withUnit("s"))
      this.targetsSetting = this.add(BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency())
      val selfVisible = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.selfSetting = this.add(BooleanSetting("Self", true, selfVisible))
      val playersVisible = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.playersSetting = this.add(BooleanSetting("Players", false, playersVisible))
      val pearlsVisible = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.pearlsSetting = this.add(BooleanSetting("Pearls", false, pearlsVisible))
      val arrowsVisible = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.arrowsSetting = this.add(BooleanSetting("Arrows", false, arrowsVisible))
      val expVisible = Predicate<Any> { ignored -> this.targetsSetting.isExpanded() }
      this.expSetting = this.add(BooleanSetting("Exp", false, expVisible))
      this.colorSetting = this.add(ColorSetting("Color", Color(-1312974337, true)))
      val thresholdVisible = Predicate<Any> { ignored -> (this.colorSetting as ColorSetting).isRainbow }
      this.thresholdSetting = this.add(NumberSetting<Double>("Threshold", 1.0, 0.1, 3.0, thresholdVisible))
      this.sampleTimer = ElapsedTimer()

      this.setDrawn(false)
   }

   private class TrailPoint(val position: Vec3) {
      val createdAtMillis: Long = System.currentTimeMillis()

   }
}
