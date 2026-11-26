package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget

open class BloomMaskPipeline : ShaderPipeline(MaskShaderRenderer.MASK_TARGET) {
   init {
      val program = CompiledShaderProgram.fromResources("base.vert", "multi/bloom_mask.frag")
      val horizontalStage: ShaderStage = DirectionalStage(
         program, true, MaskShaderRenderer.HORIZONTAL_TARGET
      )
      this.addStage(horizontalStage)
      val verticalStage: ShaderStage = DirectionalStage(
         program, false, MaskShaderRenderer.VERTICAL_TARGET
      )
      this.addStage(verticalStage)
      this.enableOutputBlending()
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
         val blurWidth = MaskShaderRenderer.getBlurWidth()
         this.uniforms.set("u_Width", blurWidth)
         this.uniforms.set("u_Sigma", blurWidth * 0.5f)
         this.uniforms.set("u_Modulate", MaskShaderRenderer.getModulate())
         this.uniforms.set("u_Bloom", MaskShaderRenderer.getBloom())
         this.uniforms.set("u_Opacity", MaskShaderRenderer.getOpacity())
         this.uniforms.set("u_Direction", this.directionX.toFloat(), this.directionY.toFloat())
      }
   }
}
