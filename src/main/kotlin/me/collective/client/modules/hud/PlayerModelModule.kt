package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.modules.HudWidgetModule
import me.collective.client.render.PostProcessShaderPass
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.ResizableHudWidgetRenderer

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.platform.Lighting.Entry
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import org.joml.Vector3f

class PlayerModelModule : HudWidgetModule("PlayerModel") {

   init {
      val widgetController = ResizableHudWidgetRenderer(this)
      widgetController.enableResizing(this)
      this.setRenderer(widgetController)
   }

   override fun calculateDimensions(): FloatArray {
      val dimensions = FloatArray(2)
      dimensions[0] = 50.0F
      dimensions[1] = 80.0F
      return dimensions
   }

   override fun renderContent(poses: PoseStack) {
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      if (camera.position() != null) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
         val quaternion = Quaternionf()

         val rotationZ: Float = 3.1415927F
         val rotation: Quaternionf = quaternion.rotateZ(rotationZ)
         val modelTranslation = Vector3f(
            0.0F, player.getBbHeight() / 2.0F, 0.0F
         )
         val x: Float = 25.0F
         val y: Float = 40.0F
         val scaleArgument: Float = 35.0F
         renderEntityPreview(poses, x, y, scaleArgument, modelTranslation, rotation, player)
      }
   }

   companion object {

      @JvmStatic
      fun renderEntityPreview(
         poses: PoseStack, x: Float, y: Float, scale: Float, modelTranslation: Vector3f, rotation: Quaternionf, entity: LivingEntity
      ) {
         RenderUtils.clearColor(
            PostProcessShaderPass.POST_TARGET, Color.black
         )
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         )
         RenderTargetUtils.overrideRenderTargets(
            PostProcessShaderPass.POST_TARGET
         )
         poses.pushPose()
         poses.translate(x.toDouble(), y.toDouble(), 50.0)
         val scaleZ: Float = -scale
         poses.scale(scale, scale, scaleZ)

         val modelZ: Float = modelTranslation.z
         val modelY: Float = modelTranslation.y
         val modelX: Float = modelTranslation.x
         poses.translate(modelX, modelY, modelZ)
         poses.mulPose(rotation)
         val lighting: Lighting = ClientAccess.collectiveMinecraft.gameRenderer.getLighting()
         val entityLightingEntry: Entry = Entry.ENTITY_IN_UI
         lighting.setupFor(entityLightingEntry)
         val submitNodes: SubmitNodeStorage = ClientAccess.collectiveMinecraft.gameRenderer.getSubmitNodeStorage()
         val cameraState: CameraRenderState = ClientAccess.collectiveMinecraft.gameRenderer.getLevelRenderState().cameraRenderState
         val clientForEntityRenderer = Minecraft.getInstance()
         val entityRenderer: EntityRenderDispatcher = clientForEntityRenderer.getEntityRenderDispatcher()
         val partialTick: Float = RenderUtils.getPartialTick()
         val entityState: EntityRenderState = entityRenderer.extractEntity(entity, partialTick)
         entityState.shadowPieces.clear()
         entityRenderer.submit(entityState, cameraState, 0.0, 0.0, 0.0, poses, submitNodes)
         RenderUtils.INSTANCE
            .flushEntities()
         poses.popPose()
         val rendererForItemLighting: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
         val itemLighting: Lighting = rendererForItemLighting.getLighting()
         val itemLightingEntry: Entry = Entry.ITEMS_FLAT
         itemLighting.setupFor(itemLightingEntry)
         RenderTargetUtils.restoreRenderTargets()
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         )
         RenderUtils.blitToMain(
            PostProcessShaderPass.POST_TARGET
         )
      }
   }
}
