package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import me.collective.client.ClientAccess

open class BlurMaskPipeline : ShaderPipeline(ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget()) {
   init {
      val program = CompiledShaderProgram.fromResources("base.vert", "multi/blur_mask.frag")
      val verticalStage: ShaderStage = DirectionalStage(
         program, false, MaskShaderRenderer.VERTICAL_TARGET
      )
      this.addStage(verticalStage)
      val horizontalStage: ShaderStage = DirectionalStage(
         program, true, MaskShaderRenderer.HORIZONTAL_TARGET
      )
      this.addStage(horizontalStage)
   }

   open class DirectionalStage(
      program: CompiledShaderProgram,
      horizontal: Boolean,
      outputTarget: RenderTarget
   ) : RenderTargetShaderStage(program, outputTarget) {
      private val directionX: Int = if (horizontal) 1 else 0
      private val directionY: Int = if (horizontal) 0 else 1

      override fun updateUniforms() {
         super.updateUniforms()
         this.uniforms.set("u_Mask", 16, MaskShaderRenderer.MASK_TARGET)
         val blurWidth = MaskShaderRenderer.getBlurWidth()
         this.uniforms.set("u_Width", blurWidth)
         this.uniforms.set("u_Sigma", 1.0f + blurWidth / 1.5f)
         this.uniforms.set("u_Direction", this.directionX.toFloat(), this.directionY.toFloat())
      }
   }
}
