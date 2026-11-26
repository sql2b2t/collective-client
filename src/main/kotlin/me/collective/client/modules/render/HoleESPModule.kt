package me.collective.client.modules.render


import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.managers.RotationManager
import me.collective.client.render.ColorUtils
import me.collective.client.managers.HoleManager
import me.collective.client.utils.world.HoleInfo
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathUtils
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class HoleESPModule : Module("HoleESP", "Highlights the spots that are safe from end crystals.", ModuleCategory.RENDER) {
   private val fillSetting: Setting<DrawMode>
   private val lineSetting: Setting<DrawMode>
   private val lineWidthSetting: Setting<Float>
   private val heightSetting: Setting<Float>
   private val radiusSetting: Setting<Int>
   private val bloomSetting: Setting<Boolean>
   private val hideOwnSetting: Setting<Boolean>
   internal val fadeSetting: Setting<Boolean>
   private val fadeRadiusSetting: Setting<Float>
   internal val trappedSetting: Setting<Boolean>
   private val trappedFillSetting: Setting<Color>
   private val trappedOutlineSetting: Setting<Color>
   internal val safeSetting: Setting<Boolean>
   private val safeFillSetting: Setting<Color>
   private val safeOutlineSetting: Setting<Color>
   internal val unsafeSetting: Setting<Boolean>
   private val unsafeFillSetting: Setting<Color>
   private val unsafeOutlineSetting: Setting<Color>

   init {
      this.fillSetting = this.add(EnumSetting<DrawMode>("Fill", DrawMode.GRADIENT))
      this.lineSetting = this.add(EnumSetting<DrawMode>("Line", DrawMode.SOLID))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 3.0F))
      this.heightSetting = this.add(NumberSetting<Float>("Height", 0.0F, -1.0F, 1.0F).withUnit("m"))
      val radiusKey = "Radius"
      this.radiusSetting = this.add(NumberSetting<Int>(radiusKey, 10, 1, 16).withUnit("m"))
      this.bloomSetting = this.add(BooleanSetting("Bloom", false))
      this.hideOwnSetting = this.add(BooleanSetting("HideOwn", false))
      this.fadeSetting = this.add(BooleanSetting("Fade", false).markVisibilityDependency())
      val fadeRadiusVisibility: Predicate<Any> = Predicate { ignored -> this.fadeSetting.isExpanded() }
      this.fadeRadiusSetting = this.add(NumberSetting<Float>("FadeRadius", 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit("m"))
      this.trappedSetting = this.add(BooleanSetting("Trapped", false).markVisibilityDependency())
      val trappedFillVisibility: Predicate<Any> = Predicate { ignored -> this.trappedSetting.isExpanded() }
      this.trappedFillSetting = this.add(ColorSetting("TrappedFill", Color(233622784, true), trappedFillVisibility))
      val trappedOutlineVisibility: Predicate<Any> = Predicate { ignored -> this.trappedSetting.isExpanded() }
      this.trappedOutlineSetting = this.add(ColorSetting("TrappedOutline", Color(-1073745408, true), trappedOutlineVisibility))
      val safe: BooleanSetting = BooleanSetting("Safe", true)
      safe.markAsGroup()
      safe.markVisibilityDependency()
      this.safeSetting = this.add(safe)
      val safeFillVisibility: Predicate<Any> = Predicate { ignored -> this.safeSetting.isExpanded() }
      this.safeFillSetting = this.add(ColorSetting("SafeFill", Color(540373280, true), safeFillVisibility))
      val safeOutlineVisibility: Predicate<Any> = Predicate { ignored -> this.safeSetting.isExpanded() }
      this.safeOutlineSetting = this.add(ColorSetting("SafeOutline", Color(-1037662665, true), safeOutlineVisibility))
      this.unsafeSetting = this.add(BooleanSetting("Unsafe", true).markAsGroup().markVisibilityDependency())
      val unsafeFillVisibility: Predicate<Any> = Predicate { ignored -> this.unsafeSetting.isExpanded() }
      this.unsafeFillSetting = this.add(ColorSetting("UnsafeFill", Color(634257408, true), unsafeFillVisibility))
      val unsafeOutlineVisibility: Predicate<Any> = Predicate { ignored -> this.unsafeSetting.isExpanded() }
      this.unsafeOutlineSetting = this.add(ColorSetting("UnsafeOutline", Color(-1077018624, true), unsafeOutlineVisibility))
   }

   override fun getInfo(): String? {
      val holeManagerSnapshot: HoleManager = ClientContext.holeManager
      val holeCount = holeManagerSnapshot.getHoles().size
      return holeCount.toString()
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val renderPartialTick = event.partialTick.toDouble()
      val previousPlayerY = ClientAccess.collectiveMinecraft.player!!.yo
      val currentPlayerY = ClientAccess.collectiveMinecraft.player!!.getY()

      val interpolatedPlayerY = Mth.lerp(renderPartialTick, previousPlayerY, currentPlayerY)
      val cameraPosition = (ClientAccess.collectiveMinecraft.gameRenderer.mainCamera()).position()

      val ownBlockBounds = AABB(ownBlockPosition())
      val holeManagerSnapshot: HoleManager = ClientContext.holeManager
      val holes: List<HoleInfo> = holeManagerSnapshot.getHoles() as List<HoleInfo>
      for (hole in holes) {
         val configuredTrapped: Boolean = this.trappedSetting.getValue()!!
         if (!configuredTrapped && hole.trapped) {
            continue
         }
         val rangePosition: BlockPos = hole.pos
         val rangeRadius: Double = (this.radiusSetting.getValue()!!).toDouble()
         if (rangePosition.closerToCenterThan(cameraPosition, rangeRadius)) {
            val playerBounds = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
            val ownIntersectionBounds = hole.bounds
            if (playerBounds.intersects(ownIntersectionBounds)) {
               val configuredHideOwn: Boolean = this.hideOwnSetting.getValue()!!
               if (configuredHideOwn) {
                  val configuredFade: Boolean = this.fadeSetting.getValue()!!
                  if (!configuredFade) {
                     continue
                  }
               }
            }
            val holeBounds = hole.bounds
            if (RotationManager.isVisible(holeBounds)) {
               var fillColor: Color = this.safeFillSetting.getValue()!!
               var outlineColor: Color = this.safeOutlineSetting.getValue()!!
               if (hole.trapped) {
                  fillColor = this.trappedFillSetting.getValue()!!
                  outlineColor = this.trappedOutlineSetting.getValue()!!
               } else {
                  if (hole.safety ==
         HoleInfo.Safety.UNSAFE) {
                     fillColor = this.unsafeFillSetting.getValue()!!
                     outlineColor = this.unsafeOutlineSetting.getValue()!!
                  }
               }
               val distancePosition: BlockPos = hole.pos
               val holeCenter: Vec3 = Vec3.atCenterOf(distancePosition)
               val cameraDistance = cameraPosition.distanceTo(holeCenter)
               val ownFadeHoleBounds = hole.bounds
               val fadeOwnHole = ownFadeHoleBounds.intersects(ownBlockBounds) &&
         this.hideOwnSetting.getValue()!!
               val configuredFade2: Boolean = this.fadeSetting.getValue()!!
               if (configuredFade2) {
                  val configuredFadeRadius: Float = this.fadeRadiusSetting.getValue()!!
                  if (cameraDistance >= configuredFadeRadius || fadeOwnHole) {
                     val cameraDistanceAsFloat = cameraDistance.toFloat()
                     val fadeRadiusAsFloat: Float = (this.fadeRadiusSetting.getValue()!!)
                     val radiusAsFloat: Float = (this.radiusSetting.getValue()!!).toFloat()

                     var alphaFactor = 1.0F
                        - MathUtils.rangeProgress(
                           cameraDistanceAsFloat, fadeRadiusAsFloat, radiusAsFloat
                        )
                     if (fadeOwnHole) {
                        alphaFactor = (interpolatedPlayerY - Math.floor(interpolatedPlayerY)).toFloat()
                     }
                     val fillAlphaSource: Color = fillColor
                     fillColor = ColorUtils.withAlpha(
                        fillColor, (alphaFactor * fillAlphaSource.getAlpha()).toInt()
                     )
                     val outlineAlphaSource: Color = outlineColor
                     outlineColor = ColorUtils.withAlpha(
                        outlineColor, (alphaFactor * outlineAlphaSource.getAlpha()).toInt()
                     )
                  }
               }
               val configuredFill: DrawMode = this.fillSetting.getValue()!!
               when (configuredFill.ordinal) {
                  1 -> {
                     val solidFillPoses: PoseStack = event.poseStack!!
                     val solidFillBounds = hole.bounds
                     val solidFillPosition: BlockPos = hole.pos
                     val solidFillBaseY = solidFillPosition.getY()
                     val configuredHeight: Float = this.heightSetting.getValue()!!

                     val solidFillMaxY = (solidFillBaseY + configuredHeight).toDouble()
                     WorldBoxRenderer.fillBox(
                        solidFillPoses, solidFillBounds.setMaxY(solidFillMaxY), fillColor
                     )
                  }
                  2 -> {
                     val gradientFillPoses: PoseStack = event.poseStack!!
                     val gradientFillBounds = hole.bounds
                     val gradientFillPosition: BlockPos = hole.pos
                     val gradientFillMaxY = gradientFillPosition.getY() + 1
                     WorldBoxRenderer.fillFadingBox(
                        gradientFillPoses, gradientFillBounds.setMaxY(gradientFillMaxY.toDouble()), fillColor
                     )
                  }
               }
               if (this.lineSetting.getValue()!! != DrawMode.NONE) {
                  val lineWidth: Float = (this.lineWidthSetting.getValue()!!)
                  if (this.lineSetting.getValue()!! == DrawMode.GRADIENT) {
                     val gradientLineBounds = hole.bounds
                     val gradientLinePosition: BlockPos = hole.pos
                     val gradientLineMaxY = gradientLinePosition.getY() + 1
                     val gradientOutlineBounds = gradientLineBounds.setMaxY(gradientLineMaxY.toDouble())
                     val gradientLinePoses: PoseStack = event.poseStack!!
                     WorldBoxRenderer.outlineFadingBox(
                        gradientLinePoses, gradientOutlineBounds, outlineColor.hashCode(), lineWidth
                     )
                  }
                  val configuredBloom: Boolean = this.bloomSetting.getValue()!!
                  if (configuredBloom) {

                     val distanceToRadius: Float = (this.radiusSetting.getValue()!!) - cameraDistance.toFloat()
                     val maximumBloomWidth: Float = lineWidth + 6.0F
                     val bloomLineWidth: Float = Math.clamp(distanceToRadius, 1.0F, maximumBloomWidth)
                     val bloomColor = ColorUtils.multiplyAlpha(
                        outlineColor, 0.15F
                     )
                     val bloomPoses: PoseStack = event.poseStack!!
                     val bloomBounds = hole.bounds
                     val bloomPosition: BlockPos = hole.pos
                     val bloomBaseY = bloomPosition.getY()
                     val configuredHeight2: Float = this.heightSetting.getValue()!!
                     val bloomMaxY = (bloomBaseY + configuredHeight2).toDouble()
                     WorldBoxRenderer.outlineBox(
                        bloomPoses, bloomBounds.setMaxY(bloomMaxY), bloomColor, bloomLineWidth
                     )
                     if (this.lineSetting.getValue()!! == DrawMode.GRADIENT) {
                        val bloomGradientBounds = hole.bounds
                        val bloomGradientPosition: BlockPos = hole.pos
                        val bloomGradientMaxY = bloomGradientPosition.getY() + 1
                        val bloomGradientOutlineBounds = bloomGradientBounds.setMaxY(bloomGradientMaxY.toDouble())
                        val bloomGradientPoses: PoseStack = event.poseStack!!
                        WorldBoxRenderer.outlineFadingBox(
                           bloomGradientPoses, bloomGradientOutlineBounds, bloomColor.hashCode(), bloomLineWidth
                        )
                     }
                  }
                  val outlinePoses: PoseStack = event.poseStack!!
                  val outlineBounds = hole.bounds
                  val outlinePosition: BlockPos = hole.pos
                  val outlineBaseY = outlinePosition.getY()
                  val configuredHeight3: Float = this.heightSetting.getValue()!!
                  val outlineMaxY = (outlineBaseY + configuredHeight3).toDouble()
                  WorldBoxRenderer.outlineBox(
                     outlinePoses, outlineBounds.setMaxY(outlineMaxY), outlineColor, lineWidth
                  )
               }
            }
         }
      }
   }

   enum class DrawMode(private val displayName: String) : NamedEntry {
      NONE("None"),
      SOLID("Solid"),
      GRADIENT("Gradient");

      override fun getName(): String {
         return this.displayName
      }

   }

   companion object {
      private fun ownBlockPosition(): BlockPos {
         return PlayerStateUtils.getLocalRoundedBlockPosition()
      }
   }
}
