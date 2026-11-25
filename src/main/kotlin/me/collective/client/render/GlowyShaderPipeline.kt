package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import me.collective.client.ClientAccess
import me.collective.client.managers.RenderTargetRegistry
import kotlin.math.ceil

class GlowyShaderPipeline : ShaderPipeline(MaskShaderRenderer.MASK_TARGET) {
   init {
      val program = CompiledShaderProgram.fromResources("base.vert", "multi/glowy.frag")
      this.addStage(GlowStage(program, VERTICAL_TARGET, 0, true))
      this.addStage(GlowStage(program, HORIZONTAL_TARGET, 0, false))
      this.addStage(GlowStage(program, VERTICAL_TARGET, 1, false))
      this.enableOutputBlending()
      this.enableLinearOutputFiltering()
   }

   private class GlowStage(
      program: CompiledShaderProgram,
      outputTarget: RenderTarget,
      private val pass: Int,
      private val downsample: Boolean
   ) : RenderTargetShaderStage(program, outputTarget) {

      override fun updateUniforms() {
         super.updateUniforms()
         val width = ceil(
            (MaskShaderRenderer.getBlurWidth() * ClientAccess.collectiveMinecraft.window.guiScale * 2.0f / 2.0f).toDouble()
         ).toInt()
         this.uniforms.set("u_Pass", this.pass)
         this.uniforms.set("u_Width", if (this.downsample) 0 else width)
         this.uniforms.set("u_Sigma", width / 3.0f)
         this.uniforms.set("u_Bloom", MaskShaderRenderer.getBloom())
         this.uniforms.set("u_Opacity", MaskShaderRenderer.getOpacity())
      }
   }

   companion object {
      private val HORIZONTAL_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_glow_horizontal", 2)
      private val VERTICAL_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_glow_vertical", 2)
   }
}
