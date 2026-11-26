package me.collective.client.render

import me.collective.client.ClientContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.awt.Color

open class BloomShaderPass : PreprocessedShaderPass("base.vert", "bloom.frag", COVERAGE_PASS) {
   init {
      this.initializeProgram()
   }

   override fun updateUniforms() {
      super.updateUniforms()

      val shaderModule = shaderModule() ?: return

      GL13.glActiveTexture(GL13.GL_TEXTURE7)
      val overlayTextureId = PostProcessShaderPass.overlayTextureId
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTextureId)

      this.uniforms.set("u_Image", shaderModule.imageSetting.value)
      this.uniforms.set("u_Overlay", 7)
      this.uniforms.set("u_OverlayAlpha", shaderModule.getOverlayAlpha())

      val lineWidth = if (shaderModule.shouldSkipHandOutline()) 0 else shaderModule.lineWidthSetting.value
      this.uniforms.set("u_Width", lineWidth)

      val fastLinesEnabled = shaderModule.fastLinesSetting.value || ClientContext.shaderRegistry.renderingHands
      this.uniforms.set("u_FastLines", fastLinesEnabled)
      this.uniforms.set("u_GlowInner", shaderModule.innerSetting.value)
      this.uniforms.set("u_GlowQuality", shaderModule.qualitySetting.value)

      val fillColor: Color = if (shaderModule.shouldSkipHandFill()) {
         ColorUtils.transparent(shaderModule.outlineColorSetting.value)
      } else {
         shaderModule.applyClusterAlpha(shaderModule.fillSetting.value)
      }
      this.uniforms.set("u_FillColor", fillColor)
      this.uniforms.set("u_OutlineColor", shaderModule.outlineColorSetting.value)
      this.uniforms.set("u_Dots", shaderModule.typeSetting.value.ordinal)
      this.uniforms.set("u_DotsRadius", shaderModule.radiusSetting.value)
      this.uniforms.set("u_DotsAlpha", shaderModule.alphaSetting.value)
      this.uniforms.set("u_Glow", shaderModule.weightSetting.value)
      this.uniforms.set("u_Fade", !shaderModule.fadeSetting.isAtMaximum)
   }
}
