package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.render.AmbienceModule
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

open class DepthOfFieldPipeline : ShaderPipeline(ClientAccess.collectiveMinecraft.mainRenderTarget) {
   init {
      val program = CompiledShaderProgram.fromResources("base.vert", "multi/dof.frag")
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

         GL13.glActiveTexture(GL13.GL_TEXTURE14)
         val capturedAmbience = ambience()
         if (capturedAmbience == null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            return
         }

         val depthTarget = capturedAmbience.getDepthRenderTarget()
         if (depthTarget == null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            return
         }

         GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTarget.depthTextureId)
         this.uniforms.set("DepthSampler", 14)
         this.uniforms.set("FocusDistance", 0.998f)
         this.uniforms.set("BlurStrength", capturedAmbience.strengthSetting.value * 10.0f)
         this.uniforms.set("u_Direction", this.directionX.toFloat(), this.directionY.toFloat())
         GL13.glActiveTexture(GL13.GL_TEXTURE0)
      }
   }

   companion object {
      private var ambience: AmbienceModule? = null

      private fun ambience(): AmbienceModule? {
         if (ambience == null) {
            ambience = ClientContext.getModule(AmbienceModule::class.java)
         }
         return ambience
      }
   }
}
