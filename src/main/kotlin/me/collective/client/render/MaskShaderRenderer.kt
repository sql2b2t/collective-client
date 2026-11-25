package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.RenderTargetRegistry
import net.minecraft.client.Minecraft
import java.awt.Color

object MaskShaderRenderer : ClientAccess {
   @JvmField
   val MASK_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_blur")

   @JvmField
   val HORIZONTAL_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_blur_horizontal")

   @JvmField
   val VERTICAL_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_blur_vertical")

   private var blurWidth: Float = 0.0f
   private var bloom: Float = 0.0f
   private var opacity: Float = 0.0f
   private var modulate: Float = 0.0f

   @JvmStatic
   fun getBlurWidth(): Float = blurWidth

   @JvmStatic
   fun getBloom(): Float = bloom

   @JvmStatic
   fun getModulate(): Float = modulate

   @JvmStatic
   fun setParameters(width: Float, bloomStrength: Float, maskOpacity: Float, modulation: Float) {
      blurWidth = width
      bloom = bloomStrength
      opacity = maskOpacity
      modulate = modulation
   }

   @JvmStatic
   fun getOpacity(): Float = opacity

   @JvmStatic
   fun renderBlur(drawMask: Runnable?, width: Float) {
      blurWidth = width
      captureMask(drawMask)
      renderEffect("blur_mask")
   }

   @JvmStatic
   @JvmOverloads
   fun captureMask(drawMask: Runnable?, clearColor: Color = Color.WHITE) {
      RenderSystem.assertOnRenderThread()
      val mainTarget = Minecraft.getInstance().mainRenderTarget
      if (drawMask != null) {
         RenderUtils.clearColor(MASK_TARGET, clearColor)
         RenderUtils.clearDepth(MASK_TARGET)
         RenderTargetUtils.setMainRenderTarget(MASK_TARGET)
         try {
            drawMask.run()
         } finally {
            RenderTargetUtils.setMainRenderTarget(mainTarget)
         }
      }
   }

   @JvmStatic
   fun renderEffect(effectId: String) {
      ClientContext.shaderRegistry.render(effectId) {}
   }
}
