package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.managers.RotationManager
import me.collective.client.render.MeshBuffer
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.render.MeshDrawContext
import me.collective.client.render.ClientRenderTypes
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathUtils

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.PoseStack.Pose
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import com.mojang.math.Axis
import java.awt.Color
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.lwjgl.opengl.GL11C

class MarkerTracerRenderer(module: TracersModule) : TracerRenderer(module) {

   private fun drawMarker(
      poseStack: PoseStack, targetYaw: Float, distance: Float, screenHeight: Float, screenWidth: Float, cameraAngles: FloatArray, color: Color
   ) {
      val yawDifference = targetYaw - cameraAngles[0]
      val relativeYaw = Mth.wrapDegrees(yawDifference)
      val angleRadians = Math.toRadians(relativeYaw.toDouble())
      val capturedCameraPitch = cameraAngles[1]
      val negatedPitchLimit: Float = (-MathConstants.NINETY_DEGREES).toFloat()
      val maximumPitch: Float = MathConstants.NINETY_DEGREES.toFloat()
      val clampedPitch = Mth.clamp(capturedCameraPitch, negatedPitchLimit, maximumPitch)
      val pitchFraction = Math.abs(clampedPitch) / MathConstants.NINETY_DEGREES
      val centerX = screenWidth * MathConstants.HALF
      val markerX = (centerX + Math.sin(angleRadians) * distance).toFloat()
      val centerY = screenHeight * MathConstants.HALF
      val cosine = Math.cos(angleRadians)

      val markerY = (centerY - cosine * (distance * Math.max(pitchFraction, 0.4F))).toFloat()
      poseStack.pushPose()
      poseStack.translate(markerX, markerY, 0.0F)
      val markerAxis = Axis.ZP
      val markerRotation = relativeYaw - MathConstants.NINETY_DEGREES
      val rotation: Quaternionf = markerAxis.rotationDegrees(markerRotation)
      poseStack.mulPose(rotation)

      val packedColor = color.hashCode()
      this.drawArrow(poseStack, -4.0F, -4.0F, 8.0F, packedColor)
      poseStack.popPose()
   }

   private fun drawArrow(
      poseStack: PoseStack, x: Float, y: Float, size: Float, packedColor: Int
   ) {
      val pose: Pose = poseStack.last()
      val matrix: Matrix4f = pose.pose()

      val vertex1X = x + 0.1F * size
      val vertex1Y = y + 0.1F * size
      val vertex2X = x + 0.1F * size
      val vertex2Y = y + 0.3F * size
      val vertex3X = x + 0.1F * size
      val vertex3Y = y + 0.7F * size
      val vertex4X = x + 0.1F * size
      val vertex4Y = y + 0.9F * size
      val vertex5X = x + 0.4F * size
      val vertex5Y = y + 0.5F * size
      val vertex6X = x + 0.25F * size
      val vertex6Y = y + 0.5F * size
      addVertex(matrix, vertex1X, vertex1Y, packedColor)
      addVertex(matrix, vertex2X, vertex2Y, packedColor)
      addVertex(matrix, vertex6X, vertex6Y, packedColor)
      addVertex(matrix, vertex6X, vertex6Y, packedColor)
      addVertex(matrix, vertex5X, vertex5Y, packedColor)
      addVertex(matrix, vertex1X, vertex1Y, packedColor)
      addVertex(matrix, vertex3X, vertex3Y, packedColor)
      addVertex(matrix, vertex4X, vertex4Y, packedColor)
      addVertex(matrix, vertex5X, vertex5Y, packedColor)
      addVertex(matrix, vertex5X, vertex5Y, packedColor)
      addVertex(matrix, vertex6X, vertex6Y, packedColor)
      addVertex(matrix, vertex3X, vertex3Y, packedColor)
   }

   private fun addVertex(
      matrix: Matrix4f, x: Float, y: Float, packedColor: Int
   ) {
      val buffer: MeshBuffer = MeshDrawContext.getBuffer()
      val vertex = buffer.addVertex(matrix, x, y, 0.0F)
      vertex.setColor(packedColor)
   }

   override fun render(poseStack: PoseStack, tickDelta: Float) {

      val cameraEntity = ClientAccess.collectiveMinecraft.cameraEntity
      val localPlayer = ClientAccess.collectiveMinecraft.player
      if (cameraEntity != null && localPlayer != null) {
         val cameraAngles = getCameraAngles()
         val localPosition = localPlayer.getPosition(tickDelta)
         MeshDrawContext.getBuffer().begin(
            Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR
         )
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val players = level.players().iterator()

         while (players.hasNext()) {
            val player = players.next() as AbstractClientPlayer
            var cameraPosition = cameraEntity.getPosition(tickDelta)
            val playerPosition = player.getPosition(tickDelta)

            val targetYaw = RotationManager.calculateRotations(
               cameraPosition, playerPosition
            )[0]

            if (!this.module.shouldSkip(player) && !player.equals(cameraEntity)) {
               val bounds = BoundingBoxUtils.getRenderBounds(
                  player, tickDelta
               )

               var opacity = 1.0F
               val ignoreVisibleSetting: Setting<Boolean> = this.module.ignoreVisibleSetting
               if (ignoreVisibleSetting.value!!) {
                  val cameraYaw = cameraAngles[0]
                  val yawDifference = Mth.degreesDifferenceAbs(targetYaw, cameraYaw)
                  cameraPosition = BoundingBoxUtils.clampToBounds(localPosition, bounds)
                  val boundsDistance = cameraPosition.distanceTo(localPosition).toFloat()
                  val firstFovSetting = ClientAccess.collectiveMinecraft.options.fov()
                  val fovValue: Float = (firstFovSetting.get() as Int).toFloat()
                  val secondFovSetting = ClientAccess.collectiveMinecraft.options.fov()
                  val secondFovValue: Int = secondFovSetting.get() as Int
                  val fovEnd: Float = (secondFovValue + 20).toFloat()
                  opacity *= MathUtils.rangeProgress(
                     yawDifference, fovValue, fovEnd
                  )

                  opacity *= MathUtils.rangeProgress(
                     boundsDistance, 0.0F, 0.1F
                  )
               }

               if (!(opacity < 0.1)) {
                  val colorPosition = player.position()
                  val color = ColorUtils.multiplyAlpha(
                     this.module.getColor(player, colorPosition), opacity
                  )
                  val capturedShadowSetting: Setting<Int> = this.module.shadowSetting

                  if (!capturedShadowSetting.isAtMinimum()) {
                     poseStack.pushPose()
                     val shadowValue = this.module.shadowSetting.value!!

                     val shadowOffset = shadowValue * 0.5F
                     poseStack.translate(shadowOffset, shadowOffset, 0.0F)
                     val configuredShadowRadius = this.module.distanceSetting.value!!
                     val scaledShadowHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat()
                     val scaledShadowWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat()

                     val shadowColor = ColorUtils.darken(
                        color, 0.25F
                     )
                     this.drawMarker(
                        poseStack, targetYaw, configuredShadowRadius, scaledShadowHeight, scaledShadowWidth, cameraAngles, shadowColor
                     )
                     poseStack.popPose()
                  }

                  val distanceSetting = this.module.distanceSetting
                  val configuredRadius = distanceSetting.value!!
                  val scaledHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat()
                  val scaledWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat()
                  this.drawMarker(
                     poseStack, targetYaw, configuredRadius, scaledHeight, scaledWidth, cameraAngles, color
                  )
               }
            }
         }

         GL11C.glEnable(2881)
         MeshDrawContext.getBuffer().draw(ClientRenderTypes.guiTriangles())

         GL11C.glDisable(2881)
      }
   }

   private fun getCameraAngles(): FloatArray {
      val cameraEntity = ClientAccess.collectiveMinecraft.cameraEntity!!
      return if (!cameraEntity.equals(ClientAccess.collectiveMinecraft.player!!)) {

         val entityAngles = FloatArray(2)
         val yawTickDelta = RenderUtils.getPartialTick()
         entityAngles[0] = cameraEntity.getYRot(yawTickDelta)
         val pitchTickDelta = RenderUtils.getPartialTick()
         entityAngles[1] = cameraEntity.getXRot(pitchTickDelta)
         entityAngles
      } else {

         val playerAngles = FloatArray(2)
         val interpolationTickDelta = RenderUtils.getPartialTick()
         val previousYawBob = ClientAccess.collectiveMinecraft.player!!.yBobO
         val currentYawBob = ClientAccess.collectiveMinecraft.player!!.yBob
         playerAngles[0] = Mth.lerp(interpolationTickDelta, previousYawBob, currentYawBob)
         val pitchTickDelta = RenderUtils.getPartialTick()
         val previousPitchBob = ClientAccess.collectiveMinecraft.player!!.xBobO
         val currentPitchBob = ClientAccess.collectiveMinecraft.player!!.xBob
         playerAngles[1] = Mth.lerp(pitchTickDelta, previousPitchBob, currentPitchBob)
         playerAngles
      }
   }
}
