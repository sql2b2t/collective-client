package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.modules.HudWidgetModule
import me.collective.client.render.PostProcessShaderPass
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.ResizableHudWidgetRenderer

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
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
      if (camera.position != null) {
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
         Lighting.setupForEntityInInventory()
         val clientForEntityRenderer = Minecraft.getInstance()
         val entityRenderer: EntityRenderDispatcher = clientForEntityRenderer.entityRenderDispatcher
         val partialTick: Float = RenderUtils.getPartialTick()
         entityRenderer.setRenderShadow(false)
         entityRenderer.render(
            entity,
            0.0,
            0.0,
            0.0,
            partialTick,
            poses,
            clientForEntityRenderer.renderBuffers().bufferSource(),
            15728880
         )
         RenderUtils.INSTANCE.flushEntities()
         entityRenderer.setRenderShadow(true)
         poses.popPose()
         Lighting.setupForFlatItems()
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
