package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ShaderRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.utils.entity.EntityUtils

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.awt.Color
import java.util.Comparator
import java.util.Locale
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Predicate
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.util.Mth
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.fish.AbstractFish
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf

class ShaderModule : Module("Shader", "Advanced ESP using shaders.", ModuleCategory.RENDER) {

   @JvmField
   val shaderSetting: Setting<ShaderChoice>
   private val rangeSetting: Setting<Int>
   @JvmField
   val fadeSetting: Setting<Float>
   @JvmField
   val lineWidthSetting: Setting<Int>
   @JvmField
   val fastLinesSetting: Setting<Boolean>
   @JvmField
   val stepSetting: Setting<Float>
   @JvmField
   val speedSetting: Setting<Float>
   @JvmField
   val rainbowSpeedSetting: Setting<Float>
   @JvmField
   val lineStrengthSetting: Setting<Float>
   @JvmField
   val fillStrengthSetting: Setting<Float>
   @JvmField
   val imageSetting: Setting<Boolean>
   @JvmField
   val overlayAlphaSetting: Setting<Float>
   @JvmField
   val glowSetting: Setting<Boolean>
   @JvmField
   val qualitySetting: Setting<Int>
   @JvmField
   val weightSetting: Setting<Float>
   @JvmField
   val innerSetting: Setting<Boolean>
   @JvmField
   val decoratorSetting: Setting<Boolean>
   @JvmField
   val typeSetting: Setting<TypeChoice>
   @JvmField
   val radiusSetting: Setting<Int>
   @JvmField
   val alphaSetting: Setting<Float>
   @JvmField
   val smoothSetting: Setting<Boolean>
   @JvmField
   val fillSetting: Setting<Color>
   @JvmField
   val fillSecondSetting: Setting<Color>
   @JvmField
   val outlineColorSetting: Setting<Color>
   @JvmField
   val outlineSecondSetting: Setting<Color>
   @JvmField
   val handsSetting: Setting<Boolean>
   @JvmField
   val handFillSetting: Setting<Boolean>
   @JvmField
   val outlineSetting: Setting<Boolean>
   @JvmField
   val optimizedSetting: Setting<Boolean>
   private val crystalsSetting: Setting<Boolean>
   private val crystalRangeSetting: Setting<Int>
   private val itemsSetting: Setting<Boolean>
   private val itemRangeSetting: Setting<Int>
   private val animalsSetting: Setting<Boolean>
   private val hostilesSetting: Setting<Boolean>
   private val playersSetting: Setting<Boolean>
   private val selfSetting: Setting<Boolean>
   private val expSetting: Setting<Boolean>
   private val pearlsSetting: Setting<Boolean>
   private val minecartsSetting: Setting<Boolean>
   private val deadSetting: Setting<Boolean>
   private val nameTagsSetting: Setting<Boolean>
   private val renderTargets: ObjectArrayList<RenderTargetEntry>
   private var minimumClusterAlpha: Float

   fun applyClusterAlpha(color: Color): Color {
      var alphaMultiplier: Float = this.minimumClusterAlpha
      if (ClientContext.shaderRegistry.renderingHands) {
         alphaMultiplier = 1.0f
      }

      return ColorUtils.withAlpha(
         color, (alphaMultiplier * color.alpha).toInt()
      )
   }

   fun getOverlayAlpha(): Float {
      if (ClientContext.shaderRegistry.renderingHands) {
         return this.overlayAlphaSetting.getValue()!!
      }
      return this.overlayAlphaSetting.getValue()!! * this.minimumClusterAlpha
   }

   fun shouldSkipHandFill(): Boolean {
      return ClientContext.shaderRegistry.renderingHands && !this.handFillSetting.getValue()!!
   }

   fun includesNameTags(): Boolean {
      return this.isToggled() && this.nameTagsSetting.getValue()!!
   }

   fun shouldSkipHandOutline(): Boolean {
      return ClientContext.shaderRegistry.renderingHands && !this.outlineSetting.getValue()!!
   }

   private fun getHandProjectionBuffer(): GpuBufferSlice {
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera()
      val projectionBuffer: ProjectionMatrixBuffer = ClientAccess.collectiveMinecraft.gameRenderer.hud3dProjectionMatrixBuffer
      val width: Float = ClientAccess.collectiveMinecraft.getWindow().getWidth().toFloat().coerceAtLeast(1.0f)
      val height: Float = ClientAccess.collectiveMinecraft.getWindow().getHeight().toFloat().coerceAtLeast(1.0f)
      val fov: Float = camera.getFov()
      val matrix = Matrix4f().setPerspective(Math.toRadians(fov.toDouble()).toFloat(), width / height, 0.05f, GameRenderer.PROJECTION_3D_HUD_Z_FAR)
      return projectionBuffer.getBuffer(matrix)
   }

   override fun getInfo(): String? {
      val shaderOption: Setting<ShaderChoice> = this.shaderSetting
      return FontRenderer.capitalize(shaderOption.getValue()!!)
   }

   private fun renderEntity(event: WorldRenderEvent, entity: Entity) {
      if (!entity.isAlive()) {
         val renderDead: Boolean = this.deadSetting.getValue()!!
         if (!renderDead && entity is LivingEntity) {
            return
         }
      }

      val poseStack: PoseStack = event.poseStack!!
      val cameraState: CameraRenderState = ClientAccess.collectiveMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
      val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val partialTick: Float = event.partialTick
      val xPartialTick: Double = partialTick.toDouble()
      val previousX: Double = entity.xOld
      val currentX: Double = entity.getX()
      val relativeX: Double = Mth.lerp(xPartialTick, previousX, currentX) - cameraPosition.x
      val yPartialTick: Double = partialTick.toDouble()
      val previousY: Double = entity.yOld
      val currentY: Double = entity.getY()
      val relativeY: Double = Mth.lerp(yPartialTick, previousY, currentY) - cameraPosition.y
      val zPartialTick: Double = partialTick.toDouble()
      val previousZ: Double = entity.zOld
      val currentZ: Double = entity.getZ()
      val relativeZ: Double = Mth.lerp(zPartialTick, previousZ, currentZ) - cameraPosition.z
      val rawFadePercent: Float = this.fadeSetting.getValue()!!

      val fadeStart: Float = rawFadePercent / 1e+02f
      val positionPartialTick: Float = partialTick
      val interpolatedPosition: Vec3 = entity.getPosition(positionPartialTick)
      val distanceToCamera: Double = cameraPosition.distanceTo(interpolatedPosition)
      var distanceAlpha: Float = (distanceToCamera / this.getEntityRange(entity)).toFloat()
      if (distanceAlpha >= fadeStart) {

         val distanceBeyondFade: Float = distanceAlpha - fadeStart

         val fadeProgressValue: Float = distanceBeyondFade / (1.0f - fadeStart)

         val fadeProgress: Float = fadeProgressValue
         distanceAlpha = 1.0f - Mth.clamp(fadeProgress, 0.0f, 1.0f)
      } else {

         distanceAlpha = 1.0f
      }

      val alphaForVisibility: Double = distanceAlpha.toDouble()
      if (alphaForVisibility <= 0.01) {
         return
      }

      val renderColorArgb: Byte = -1
      ShaderRegistry.setRenderColor(renderColorArgb.toInt())
      RenderUtils.setColorAlpha(distanceAlpha)
      val dispatcherForExtraction: EntityRenderDispatcher = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
      val extractPartialTick: Float = partialTick
      val renderState: EntityRenderState = dispatcherForExtraction.extractEntity(entity, extractPartialTick)
      val dispatcherForRenderer: EntityRenderDispatcher = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
      val entityRenderer: EntityRenderer<Entity, EntityRenderState> = dispatcherForRenderer.getRenderer(entity) as EntityRenderer<Entity, EntityRenderState>
      val renderOffset: Vec3 = entityRenderer.getRenderOffset(renderState)
      poseStack.pushPose()
      val translatedX: Double = relativeX + renderOffset.x
      val translatedY: Double = relativeY + renderOffset.y
      val translatedZ: Double = relativeZ + renderOffset.z
      poseStack.translate(translatedX, translatedY, translatedZ)

      val submitNodes: SubmitNodeCollector = ShaderRegistry.getSubmitNodes()
      entityRenderer.submit(renderState, poseStack, submitNodes, cameraState)

      ShaderRegistry.finishFeatureRendering()
      poseStack.popPose()

      RenderUtils.setColorAlpha(1.0f)
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val minimumAlphaCandidate: Optional<Float> = this.renderTargets.stream()
         .map(this::getTargetClusterAlpha)
         .min(Comparator.naturalOrder())

      this.minimumClusterAlpha = minimumAlphaCandidate.orElse(1.0f)
   }

   @EventHandler(priority = -1000)
   private fun onWorldRender(event: WorldRenderEvent.Overlay) {

      EntityCullingOverrides.disableCulling()
      event.poseStack!!.pushPose()
      val nameTagTargetSupplier = NameTagsModule.NAME_TAG_RENDER_TARGET
      val nameTagTarget: RenderTarget? = nameTagTargetSupplier.get() as RenderTarget?
      val shader: ShaderChoice = this.shaderSetting.getValue()!!
      val shaderId: String = shader.getId()
      val targetsToClear: ObjectArrayList<RenderTargetEntry> = this.renderTargets
      targetsToClear.clear()
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (this.isShaderTarget(entity)) {
            val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
            val entityPosition: Vec3 = entity.position()
            val negativeDistanceSquared: Double = -cameraPosition.distanceToSqr(entityPosition)
            val targetsForAdd: ObjectArrayList<RenderTargetEntry> = this.renderTargets
            val target = RenderTargetEntry(entity, negativeDistanceSquared)
            targetsForAdd.add(target)
         }
      }

      this.renderTargets.sortWith(Comparator.comparingDouble { entry: RenderTargetEntry -> entry.negativeDistanceSquared })
      val handsEnabled: Boolean = this.handsSetting.getValue()!!
      if (handsEnabled) {
         val optimizedHands: Boolean = this.optimizedSetting.getValue()!!
         if (optimizedHands) {
            this.renderHands(event)
         }
      }

      val nameTagsEnabled: Boolean = this.nameTagsSetting.getValue()!!
      if (nameTagsEnabled) {
         val nameTagsForBlit = nameTagsModule
         if (nameTagsForBlit!!.isToggled()) {
            val mainTarget = ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()
            val mainColorTexture: GpuTextureView = mainTarget.getColorTextureView()!!
            nameTagTarget!!.blitAndBlendToTexture(mainColorTexture, mainTarget.depthTextureView!!)
         }
      }

      val registryForMainPass: ShaderRegistry = ClientContext.shaderRegistry
      val mainPass = Runnable {
         val callbackNameTagsEnabled: Boolean = this.nameTagsSetting.getValue()!!
         if (callbackNameTagsEnabled) {
            val callbackNameTagsModule = nameTagsModule
            if (callbackNameTagsModule!!.isToggled()) {
               ShaderRegistry.getBufferSource()
                  .setColorArgb(-1)
               RenderUtils.blitToMain(nameTagTarget)
            }
         }

         val targetsForRender: ObjectArrayList<RenderTargetEntry> = this.renderTargets
         val renderTarget: Consumer<RenderTargetEntry> = Consumer { targetToRender ->
            val entityToRender: Entity? = targetToRender.entity
            this.renderEntity(event, entityToRender!!)
         }
         targetsForRender.forEach(renderTarget)
         ShaderRegistry.getBufferSource()
            .setColorArgb(-1)
         val callbackOptimizedEnabled: Boolean = this.optimizedSetting.getValue()!!
         if (callbackOptimizedEnabled) {
            val callbackHandsEnabled: Boolean = this.handsSetting.getValue()!!
            if (callbackHandsEnabled) {
               ClientContext.shaderRegistry.renderingHands = true
               this.renderHands(event)
               ClientContext.shaderRegistry.renderingHands = false
            }
         }
      }
      registryForMainPass.render(shaderId, mainPass)
      val separatePassOption: Setting<Boolean> = this.optimizedSetting
      val optimizedPassEnabled: Boolean = separatePassOption.getValue()!!
      if (!optimizedPassEnabled) {
         val separateHandsOption: Setting<Boolean> = this.handsSetting
         val separateHandsEnabled: Boolean = separateHandsOption.getValue()!!
         if (separateHandsEnabled) {
            this.renderHands(event)
            ClientContext.shaderRegistry.renderingHands = true
            val registryForHandsPass: ShaderRegistry = ClientContext.shaderRegistry
            val handsPass = Runnable {
               ShaderRegistry.getBufferSource()
                  .setColorArgb(-1)
               this.renderHands(event)
            }
            registryForHandsPass.render(shaderId, handsPass)
            ClientContext.shaderRegistry.renderingHands = false
         }
      }

      val poseForPop: PoseStack = event.poseStack!!
      poseForPop.popPose()

      EntityCullingOverrides.restoreCulling()
   }

   fun blendSettingColors(firstSetting: Setting<Color>, secondSetting: Setting<Color>): Color {
      val firstColorValue: Color = firstSetting.getValue()!!
      val secondColor: Color = secondSetting.getValue()!!
      return this.blendColorsHsb(firstColorValue, secondColor)
   }

   private fun getTargetClusterAlpha(target: RenderTargetEntry): Float {
      val entity: Entity? = target.entity
      if (entity != null) {
         val noRenderForToggle = noRenderModule
         if (noRenderForToggle!!.isToggled()) {
            val noClusterEnabled: Boolean = noRenderModule!!.noClusterSetting.getValue()!!
            if (noClusterEnabled) {
               val playerPositionValue: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
               val entityPosition: Vec3 = entity.position()
               val distance: Double = playerPositionValue.distanceTo(entityPosition)
               val entityWidth: Float = entity.getBbWidth()
               if (distance <= entityWidth && entity != ClientAccess.collectiveMinecraft.player!!) {
                  val distanceFraction: Float = (distance / entityWidth).toFloat()
                  val rawClusterAlphaPercent: Int = noRenderModule!!.clusterAlphaSetting.getValue()!!

                  val clusterAlpha: Float = rawClusterAlphaPercent / 255.0f

                  return clusterAlpha + distanceFraction * (1.0f - clusterAlpha)
               }
            }
         }
      }

      return 1.0f
   }

   fun blendColorsHsb(firstColor: Color, secondColor: Color): Color {
      val firstRed: Int = firstColor.red
      val firstGreen: Int = firstColor.green
      val firstBlue: Int = firstColor.blue
      val firstHsb = Color.RGBtoHSB(firstRed, firstGreen, firstBlue, null)
      val secondRed: Int = secondColor.red
      val secondGreen: Int = secondColor.green
      val secondBlue: Int = secondColor.blue
      val secondHsb = Color.RGBtoHSB(secondRed, secondGreen, secondBlue, null)
      val hueDifference: Float = secondHsb[0] - firstHsb[0]
      if (hueDifference > 0.5f) {
         val firstHueToWrap: Float = firstHsb[0]
         firstHsb[0] = firstHueToWrap + 1.0f
      } else {
         if (hueDifference < -0.5f) {
            val secondHueToWrap: Float = secondHsb[0]
            secondHsb[0] = secondHueToWrap + 1.0f
         }
      }

      val hsbComponents = FloatArray(3)
      val firstHue: Float = firstHsb[0]
      val weightedFirstHue: Float = firstHue * 0.5f
      val secondHue: Float = secondHsb[0]
      hsbComponents[0] = weightedFirstHue + secondHue * 0.5f
      val firstSaturation: Float = firstHsb[1]
      val weightedFirstSaturation: Float = firstSaturation * 0.5f
      val secondSaturation: Float = secondHsb[1]
      hsbComponents[1] = weightedFirstSaturation + secondSaturation * 0.5f

      val firstBrightness: Float = firstHsb[2]
      val weightedFirstBrightness: Float = firstBrightness * 0.5f
      val secondBrightness: Float = secondHsb[2]
      hsbComponents[2] = weightedFirstBrightness + secondBrightness * 0.5f
      val blendedHsb = hsbComponents
      val hueBeforeWrap: Float = blendedHsb[0]
      val offsetHue: Float = hueBeforeWrap + 1.0f
      blendedHsb[0] = offsetHue % 1.0f
      val hueForColor: Float = blendedHsb[0]
      val saturationForColor: Float = blendedHsb[1]
      val blendedBrightness: Float = blendedHsb[2]
      val blendedSaturation: Float = saturationForColor
      val blendedHue: Float = hueForColor
      val blendedColor: Color = Color.getHSBColor(blendedHue, blendedSaturation, blendedBrightness)
      val firstAlpha: Float = firstColor.alpha.toFloat()
      val normalizedFirstAlpha: Float = firstAlpha / 255.0f
      val weightedFirstAlpha: Float = normalizedFirstAlpha * 0.5f
      val secondAlpha: Float = secondColor.alpha.toFloat()
      val normalizedSecondAlpha: Float = secondAlpha / 255.0f
      return ColorUtils.withAlpha(
         blendedColor, weightedFirstAlpha + normalizedSecondAlpha * 0.5f
      )
   }

   private fun renderHands(event: WorldRenderEvent) {
      val clientForCameraEntity = ClientAccess.collectiveMinecraft
      val cameraEntity = clientForCameraEntity.getCameraEntity()
      val cameraSleeping: Boolean = if (cameraEntity is LivingEntity) cameraEntity.isSleeping() else false
      RenderSystem.backupProjectionMatrix()

      val projectionBuffer: GpuBufferSlice = this.getHandProjectionBuffer()
      val projectionType: ProjectionType = ProjectionType.PERSPECTIVE
      RenderSystem.setProjectionMatrix(projectionBuffer, projectionType)
      val cameraRotation: Quaternionf = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().rotation()
      val inverseRotationDestination = Quaternionf()
      val inverseCameraRotation: Quaternionf = cameraRotation.conjugate(inverseRotationDestination)
      val viewMatrix = Matrix4f()
      viewMatrix.rotation(inverseCameraRotation)
      val cameraState = ClientAccess.collectiveMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
      ClientAccess.collectiveMinecraft.gameRenderer.renderItemInHand(cameraState, event.partialTick, viewMatrix)
      RenderSystem.restoreProjectionMatrix()
   }

   fun isShaderTarget(entity: Entity): Boolean {
      val range: Float = this.getEntityRange(entity)
      val entityBoundsValue: AABB = entity.getBoundingBox()
      val outsideViewOrRange: Boolean
      if (RotationManager.isVisible(entityBoundsValue)) {
         outsideViewOrRange = ClientAccess.collectiveMinecraft.player!!.distanceTo(entity) > range
      } else {
         outsideViewOrRange = true
      }

      if (entity is Player) {
         if (entity.isDeadOrDying()) {
            return false
         }
      }

      if (outsideViewOrRange) {
         return false
      }

      if (EntityUtils.isFirstPersonCameraEntity(entity)) {
         return false
      }

      var detachedView: Boolean = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().isDetached()
      if (!detachedView) {
         val freecamForToggle = freecamModule
         detachedView = freecamForToggle!!.isToggled()
      }

      if (entity is Player) {
         if (detachedView && this.selfSetting.getValue()!! && entity == ClientAccess.collectiveMinecraft.player!!) {
            return this.playersSetting.getValue()!!
         }
         return this.playersSetting.getValue()!! && entity != ClientAccess.collectiveMinecraft.player!!
      }

      return (entity is ItemEntity && this.itemsSetting.getValue()!!) ||
         (entity is ThrownExperienceBottle && this.expSetting.getValue()!!) ||
         ((entity is AgeableMob || entity is AbstractFish || entity is Squid) &&
         this.animalsSetting.getValue()!!) ||
         (entity is Enemy && this.hostilesSetting.getValue()!!) ||
         (entity is ThrownEnderpearl && this.pearlsSetting.getValue()!!) ||
         (entity is EndCrystal && this.crystalsSetting.getValue()!!) ||
         (entity is AbstractMinecart && this.minecartsSetting.getValue()!!)
   }

   private fun getEntityRange(entity: Entity): Float {
      if (entity is EndCrystal) {
         return this.crystalRangeSetting.getValue()!!.toFloat()
      }
      if (entity is ItemEntity) {
         return this.itemRangeSetting.getValue()!!.toFloat()
      }
      return this.rangeSetting.getValue()!!.toFloat()
   }

   init {
      this.shaderSetting = this.add(EnumSetting<ShaderChoice>("Shader", ShaderChoice.SOLID))
      this.rangeSetting = this.add(NumberSetting<Int>("Range", 60, 1, 300).withUnit("m"))
      this.fadeSetting = this.add(NumberSetting<Float>("Fade", 100.0f, 25.0f, 100.0f).withUnit("%"))
      this.lineWidthSetting = this.add(NumberSetting<Int>("LineWidth", 1, 0, 10))
      val fastLinesVisibility: Predicate<Boolean> = Predicate { ignored -> this.lineWidthSetting.getValue()!! > 2 }
      this.fastLinesSetting = this.add(BooleanSetting("FastLines", false, fastLinesVisibility))
      val stepVisibility: Predicate<Float> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.GRADIENT }
      this.stepSetting = this.add(NumberSetting<Float>("Step", 0.5f, 0.1f, 2.0f, stepVisibility))
      val speedVisibility: Predicate<Float> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.GRADIENT }
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.1f, 5.0f, speedVisibility))
      val rainbowSpeedVisibility: Predicate<Float> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.RAINBOW }
      this.rainbowSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.0f, 5.0f, rainbowSpeedVisibility))
      val lineStrengthVisibility: Predicate<Float> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.RAINBOW }
      this.lineStrengthSetting = this.add(NumberSetting<Float>("LineStrength", 1.0f, 0.0f, 5.0f, lineStrengthVisibility))
      val fillStrengthVisibility: Predicate<Float> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.RAINBOW }
      this.fillStrengthSetting = this.add(NumberSetting<Float>("FillStrength", 1.0f, 0.0f, 5.0f, fillStrengthVisibility))
      val imageRegistered: Setting<Boolean> = this.add(BooleanSetting("Image", false).markVisibilityDependency())
      this.imageSetting = imageRegistered
      this.overlayAlphaSetting = this.add(NumberSetting<Float>("OverlayAlpha", 1.0f, 0.0f, 1.0f).dependsOn(imageRegistered))
      val glowVisibility: Predicate<Boolean> = Predicate { ignored -> this.lineWidthSetting.getValue()!! > 0 }
      val glowRegistered: Setting<Boolean> = this.add(BooleanSetting("Glow", false, glowVisibility).markAsGroup().markVisibilityDependency())
      this.glowSetting = glowRegistered
      this.qualitySetting = this.add(NumberSetting<Int>("Quality", 1, 1, 6).dependsOn(glowRegistered))
      this.weightSetting = this.add(NumberSetting<Float>("Weight", 1.0f, 0.0f, 10.0f).dependsOn(glowRegistered))
      this.innerSetting = this.add(BooleanSetting("Inner", true).dependsOn(glowRegistered))
      val decoratorRegistered: Setting<Boolean> = this.add(BooleanSetting("Decorator", false).markAsGroup().markVisibilityDependency())
      this.decoratorSetting = decoratorRegistered
      this.typeSetting = this.add(EnumSetting<TypeChoice>("Type", TypeChoice.NONE).dependsOn(decoratorRegistered))
      this.radiusSetting = this.add(NumberSetting<Int>("Radius", 5, 2, 20).dependsOn(decoratorRegistered))
      this.alphaSetting = this.add(NumberSetting<Float>("Alpha", 1.0f, 0.0f, 1.0f).dependsOn(decoratorRegistered))
      val colorsRegistered: Setting<Boolean> = this.add(BooleanSetting("Colors", false).markAsGroup().markVisibilityDependency())
      this.smoothSetting = this.add(BooleanSetting("Smooth", true).dependsOn(colorsRegistered))
      this.fillSetting = this.add(ColorSetting("Fill", Color(2025691647, true)).dependsOn(colorsRegistered))
      val fillSecondVisibility: Predicate<Color> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.GRADIENT }
      this.fillSecondSetting = this.add(ColorSetting("FillSecond", Color(1936677274, true), fillSecondVisibility)
         .dependsOn(colorsRegistered))
      this.outlineColorSetting = this.add(ColorSetting("Outline", Color(-4351489, true)).dependsOn(colorsRegistered))
      val outlineSecondVisibility: Predicate<Color> = Predicate { ignored -> this.shaderSetting.getValue() == ShaderChoice.GRADIENT }
      this.outlineSecondSetting = this.add(ColorSetting("OutlineSecond", Color(-8625746, true), outlineSecondVisibility)
         .dependsOn(colorsRegistered))
      val targetsRegistered: Setting<Boolean> = this.add(BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency())
      val handsRegistered: Setting<Boolean> = this.add(BooleanSetting("Hands", true).markVisibilityDependency().dependsOn(targetsRegistered))
      this.handsSetting = handsRegistered
      this.handFillSetting = this.add(BooleanSetting("Fill", true).dependsOn(targetsRegistered, handsRegistered))
      this.outlineSetting = this.add(BooleanSetting("Outline", true).dependsOn(targetsRegistered, handsRegistered))
      this.optimizedSetting = this.add(BooleanSetting("Optimized", true).dependsOn(targetsRegistered, handsRegistered))
      val crystalsRegistered: Setting<Boolean> = this.add(BooleanSetting("Crystals", false).markVisibilityDependency().dependsOn(targetsRegistered))
      this.crystalsSetting = crystalsRegistered
      this.crystalRangeSetting = this.add(NumberSetting<Int>("Range", 60, 1, 300).withUnit("m")
         .dependsOn(targetsRegistered, crystalsRegistered))
      val itemsRegistered: Setting<Boolean> = this.add(BooleanSetting("Items", false).markVisibilityDependency().dependsOn(targetsRegistered))
      this.itemsSetting = itemsRegistered
      this.itemRangeSetting = this.add(NumberSetting<Int>("Range", 60, 1, 300).withUnit("m")
         .dependsOn(targetsRegistered, itemsRegistered))
      this.animalsSetting = this.add(BooleanSetting("Animals", false).dependsOn(targetsRegistered))
      this.hostilesSetting = this.add(BooleanSetting("Hostiles", false).dependsOn(targetsRegistered))
      val playersRegistered: Setting<Boolean> = this.add(BooleanSetting("Players", false).dependsOn(targetsRegistered))
      this.playersSetting = playersRegistered
      this.selfSetting = this.add(BooleanSetting("Self", false).dependsOn(targetsRegistered, playersRegistered))
      this.expSetting = this.add(BooleanSetting("Exp", false).dependsOn(targetsRegistered))
      this.pearlsSetting = this.add(BooleanSetting("Pearls", false).dependsOn(targetsRegistered))
      this.minecartsSetting = this.add(BooleanSetting("Minecarts", false).dependsOn(targetsRegistered))
      this.deadSetting = this.add(BooleanSetting("Dead", false).dependsOn(targetsRegistered))
      this.nameTagsSetting = this.add(BooleanSetting("NameTags", false).dependsOn(targetsRegistered))

      this.renderTargets = ObjectArrayList(128)

      this.minimumClusterAlpha = 1.0f
      this.radiusSetting.setConfigName("DecoratorRadius")
      this.alphaSetting.setConfigName("DecoratorAlpha")
      this.handFillSetting.setConfigName("HandsFill")
      this.outlineSetting.setConfigName("HandsOutline")
      this.speedSetting.setConfigName("GradientSpeed")
      this.itemRangeSetting.setConfigName("ItemRange")
      this.crystalRangeSetting.setConfigName("CrystalRange")
      this.fadeSetting.setBoundaryLabel("None", SettingLabelCondition.MAX)
      this.fadeSetting.addVisibilityPredicate { ignored -> this.shaderSetting.getValue() != ShaderChoice.SOLID }
      this.handFillSetting.addVisibilityPredicate { ignored -> !this.optimizedSetting.getValue()!! }
      this.outlineSetting.addVisibilityPredicate { ignored -> !this.optimizedSetting.getValue()!! }
   }

   private class RenderTargetEntry(
      val entity: Entity?,
      val negativeDistanceSquared: Double
   )

   enum class ShaderChoice(private val displayName: String) : NamedEntry {
      SOLID("Solid"),
      RAINBOW("Rainbow"),
      GRADIENT("Gradient"),
      BLOOM("Bloom"),
      CUSTOM("Custom");

      override fun getName(): String {
         return this.displayName
      }

      fun getId(): String {
         return this.displayName.lowercase(Locale.ROOT)
      }

   }

   enum class TypeChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      DOTS("Dots"),
      GRID("Grid");

      override fun getName(): String {
         return this.displayName
      }

   }

   companion object {
      private var nameTagsModule: NameTagsModule? = null
      private var freecamModule: FreecamModule? = null
      private var noRenderModule: NoRenderModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         nameTagsModule = registry.getModuleOrNull(NameTagsModule::class.java)
         freecamModule = registry.getModuleOrNull(FreecamModule::class.java)
         noRenderModule = registry.getModuleOrNull(NoRenderModule::class.java)
      }
   }
}
