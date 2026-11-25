package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.LineMeshRenderer
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.awt.Color
import net.minecraft.client.CameraType
import net.minecraft.client.Options
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Quaternionfc

class SkeletonModule : Module("Skeleton", "Draws skeletons for players.", ModuleCategory.RENDER) {
   private val bloomSetting: Setting<Boolean>
   private val selfSetting: Setting<Boolean>
   private val lineWidthSetting: Setting<Float>
   private val colorSetting: Setting<Color>

   private fun applyModelPartRotation(poseStack: PoseStack, part: ModelPart) {
      if (part.zRot != 0.0f) {
         poseStack.mulPose(Axis.ZP.rotation(part.zRot))
      }

      if (part.yRot != 0.0f) {
         poseStack.mulPose(Axis.YN.rotation(part.yRot))
      }

      if (part.xRot != 0.0f) {
         poseStack.mulPose(Axis.XN.rotation(part.xRot))
      }
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val poseStack: PoseStack = event.poseStack!!
      val partialTick = event.partialTick
      LineMeshRenderer.begin(poseStack, this.lineWidthSetting.getValue())
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (player in level.players()) {
         val freecam = freecamModule!!
         var selfVisible = true
         if (!freecam.isToggled()) {
            val options: Options = ClientAccess.collectiveMinecraft.options
            selfVisible = options.getCameraType() != CameraType.FIRST_PERSON
         }
         if (player === ClientAccess.collectiveMinecraft.player!!) {
            if (!selfVisible) {
               continue
            }
            if (!this.selfSetting.getValue()) {
               continue
            }
         }
         if (player.isDeadOrDying()) {
            continue
         }
         val color = this.colorSetting.getValue()
         val relativePosition = this.getCameraRelativePosition(player, partialTick)
         val selectedDispatcher: EntityRenderDispatcher = ClientAccess.collectiveMinecraft.entityRenderDispatcher
         val renderer = selectedDispatcher.getRenderer(player) as PlayerRenderer
         val model = renderer.model as PlayerModel
         val statePartialTick = event.partialTick
         val renderState = renderer.createRenderState(player, statePartialTick) as PlayerRenderState
         model.setupAnim(renderState)
         val bodyYaw = renderState.bodyRot
         val targetSwimPitch: Float
         if (renderState.isInWater) {
            targetSwimPitch = -9e+01f - renderState.xRot
         } else {
            targetSwimPitch = -9e+01f
         }
         val swimAmount = renderState.swimAmount
         val swimPitch = Mth.lerp(swimAmount, 0.0f, targetSwimPitch)
         val visuallySwimming = player.isVisuallySwimming()
         val crouching = player.isShiftKeyDown()
         val fallFlying = player.isFallFlying()
         val head = model.head
         val leftArm = model.leftArm
         val rightArm = model.rightArm
         val leftLeg = model.leftLeg
         val rightLeg = model.rightLeg
         poseStack.pushPose()
         poseStack.translate(relativePosition.x, relativePosition.y, relativePosition.z)
         val bodyRotation = Quaternionf()
         var renderScalarScratch = (bodyYaw + 1.8e+02f).toDouble()
         renderScalarScratch *= 3.141592653589793
         renderScalarScratch /= 180.0
         val bodyYawRotation = bodyRotation.setAngleAxis(renderScalarScratch.toFloat(), 0.0f, -1.0f, 0.0f)
         poseStack.mulPose(bodyYawRotation)
         if (fallFlying) {
            val fallFlyingScale = renderState.fallFlyingScale()
            if (!renderState.isAutoSpinAttack) {
               val flyingPitchAxis = Axis.XP
               val flyingPitchAngle = fallFlyingScale * (-9e+01f - renderState.xRot)
               val flyingPitchRotation = flyingPitchAxis.rotationDegrees(flyingPitchAngle)
               poseStack.mulPose(flyingPitchRotation)
            }
            if (renderState.flyingYRot != 0.0f) {
               val flyingYawRotationAxis = Axis.YP
               val flyingYawAngle = renderState.flyingYRot
               val flyingYawRotation = flyingYawRotationAxis.rotation(flyingYawAngle)
               poseStack.mulPose(flyingYawRotation)
            }
         } else if (renderState.swimAmount > 0.0f) {
            val swimPitchRotationAxis = Axis.XP
            val swimPitchRotation = swimPitchRotationAxis.rotationDegrees(swimPitch)
            poseStack.mulPose(swimPitchRotation)
            if (visuallySwimming) {
               poseStack.translate(0.0f, -1.0f, 0.3f)
            }
         }
         val torsoStartY = if (crouching) 0.6 else 0.699999988079071
         renderScalarScratch = if (crouching) 0.23000000417232513 else 0.0
         LineMeshRenderer.addVertex(0.0, torsoStartY, renderScalarScratch, color)
         val torsoEndY = if (crouching) 1.05 else 1.399999976158142
         LineMeshRenderer.addVertex(0.0, torsoEndY, 0.0, color)
         val shoulderStartY = if (crouching) 1.0499999523162842 else 1.350000023841858
         LineMeshRenderer.addVertex(-0.3700000047683716, shoulderStartY, 0.0, color)
         val shoulderEndY = if (crouching) 1.0499999523162842 else 1.350000023841858
         LineMeshRenderer.addVertex(0.3700000047683716, shoulderEndY, 0.0, color)
         val hipStartY = if (crouching) 0.6000000238418579 else 0.699999988079071
         renderScalarScratch = if (crouching) 0.23000000417232513 else 0.0
         LineMeshRenderer.addVertex(-0.15000000596046448, hipStartY, renderScalarScratch, color)
         val hipEndY = if (crouching) 0.6000000238418579 else 0.699999988079071
         renderScalarScratch = if (crouching) 0.23000000417232513 else 0.0
         LineMeshRenderer.addVertex(0.15000000596046448, hipEndY, renderScalarScratch, color)
         poseStack.pushPose()
         val headOffsetY = if (crouching) 1.05f else 1.4f
         poseStack.translate(0.0f, headOffsetY, 0.0f)
         this.applyModelPartRotation(poseStack, head)
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color)
         LineMeshRenderer.addVertex(
            0.0, 0.15000000596046448, 0.0, color
         )
         poseStack.popPose()
         poseStack.pushPose()
         val rightLegOffsetY = if (crouching) 0.6f else 0.7f
         val rightLegOffsetZ = if (crouching) 0.23f else 0.0f
         poseStack.translate(0.15f, rightLegOffsetY, rightLegOffsetZ)
         this.applyModelPartRotation(poseStack, rightLeg)
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color)
         LineMeshRenderer.addVertex(
            0.0, -0.6000000238418579, 0.0, color
         )
         poseStack.popPose()
         poseStack.pushPose()
         val leftLegOffsetY = if (crouching) 0.6f else 0.7f
         val leftLegOffsetZ = if (crouching) 0.23f else 0.0f
         poseStack.translate(-0.15f, leftLegOffsetY, leftLegOffsetZ)
         this.applyModelPartRotation(poseStack, leftLeg)
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color)
         LineMeshRenderer.addVertex(
            0.0, -0.6000000238418579, 0.0, color
         )
         poseStack.popPose()
         poseStack.pushPose()
         val rightArmOffsetY = if (crouching) 1.05f else 1.35f
         poseStack.translate(0.37f, rightArmOffsetY, 0.0f)
         this.applyModelPartRotation(poseStack, rightArm)
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color)
         LineMeshRenderer.addVertex(
            0.0, -0.550000011920929, 0.0, color
         )
         poseStack.popPose()
         poseStack.pushPose()
         val leftArmOffsetY = if (crouching) 1.05f else 1.35f
         poseStack.translate(-0.37f, leftArmOffsetY, 0.0f)
         this.applyModelPartRotation(poseStack, leftArm)
         LineMeshRenderer.addVertex(0.0, 0.0, 0.0, color)
         LineMeshRenderer.addVertex(
            0.0, -0.550000011920929, 0.0, color
         )
         poseStack.popPose()
         poseStack.popPose()
      }
      val mainTarget: RenderTarget = ClientAccess.collectiveMinecraft.mainRenderTarget
      val maskTarget = MaskShaderRenderer.MASK_TARGET
      RenderUtils.clearColor(maskTarget, this.colorSetting.getValue())
      RenderUtils.clearDepth(
         MaskShaderRenderer.MASK_TARGET
      )
      RenderTargetUtils.setMainRenderTarget(
         MaskShaderRenderer.MASK_TARGET
      )
      LineMeshRenderer.draw()
      RenderTargetUtils.setMainRenderTarget(mainTarget)
      RenderUtils.blitToMain(
         MaskShaderRenderer.MASK_TARGET
      )
      if (this.bloomSetting.getValue()) {
         val bloomLineWidth = this.lineWidthSetting.getValue()
         val bloomWidth = 6.0f + bloomLineWidth
         MaskShaderRenderer.setParameters(
            bloomWidth, 1.5f, 1.0f, 2.0f
         )
         MaskShaderRenderer.renderEffect("bloom_mask")
      }
   }

   private fun getCameraRelativePosition(entity: Entity, partialTick: Float): Vec3 {
      val entityPosition = entity.getPosition(partialTick)
      val gameRenderer: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val cameraPosition: Vec3 = gameRenderer.mainCamera.position
      return entityPosition.subtract(cameraPosition)
   }

   init {
      this.bloomSetting = this.add(BooleanSetting("Bloom", false))
      this.selfSetting = this.add(BooleanSetting("Self", true))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.1f, 3.0f))
      this.colorSetting = this.add(ColorSetting("Color", Color(-4351489, true)))

      this.setDrawn(false)
   }

   companion object {
      private var freecamModule: FreecamModule? = null

      @JvmStatic
      fun bindModuleDependencies(registry: ModuleRegistry) {
         freecamModule = registry.getModule(
            FreecamModule::class.java
         ) as FreecamModule
      }
   }

}
