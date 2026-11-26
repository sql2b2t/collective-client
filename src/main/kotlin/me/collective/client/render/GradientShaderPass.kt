package me.collective.client.render

import me.collective.client.ClientContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

open class GradientShaderPass : PreprocessedShaderPass("base.vert", "gradient.frag", COVERAGE_PASS) {
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
      this.uniforms.set("u_GlowQuality", shaderModule.qualitySetting.value)
      this.uniforms.set("u_GlowInner", shaderModule.innerSetting.value)
      this.uniforms.set("u_Smooth", shaderModule.smoothSetting.value)

      val elapsedMillis = (System.currentTimeMillis() - ShaderPass.START_TIME_MILLIS).toFloat()
      val animationTime = elapsedMillis * 0.01f * shaderModule.speedSetting.value * 0.15f
      this.uniforms.set("u_Time", animationTime)

      if (shaderModule.shouldSkipHandFill()) {
         this.uniforms.set("u_FillColor", ColorUtils.transparent(shaderModule.fillSetting.value))
         this.uniforms.set("u_FillColor2", ColorUtils.transparent(shaderModule.fillSecondSetting.value))
         this.uniforms.set("u_FillColor3", ColorUtils.transparent(shaderModule.fillSecondSetting.value))
      } else {
         this.uniforms.set("u_FillColor", shaderModule.applyClusterAlpha(shaderModule.fillSetting.value))
         val blendedFillColor = shaderModule.blendSettingColors(shaderModule.fillSetting, shaderModule.fillSecondSetting)
         this.uniforms.set("u_FillColor2", shaderModule.applyClusterAlpha(blendedFillColor))
         this.uniforms.set("u_FillColor3", shaderModule.applyClusterAlpha(shaderModule.fillSecondSetting.value))
      }

      this.uniforms.set("u_OutlineColor", shaderModule.outlineColorSetting.value)
      val blendedOutlineColor = shaderModule.blendSettingColors(shaderModule.outlineColorSetting, shaderModule.outlineSecondSetting)
      this.uniforms.set("u_OutlineColor2", shaderModule.applyClusterAlpha(blendedOutlineColor))
      this.uniforms.set("u_OutlineColor3", shaderModule.outlineSecondSetting.value)
      this.uniforms.set("u_Dots", shaderModule.typeSetting.value.ordinal)
      this.uniforms.set("u_DotsRadius", shaderModule.radiusSetting.value)
      this.uniforms.set("u_DotsAlpha", shaderModule.alphaSetting.value)
      this.uniforms.set("u_Step", shaderModule.stepSetting.value * 4.0f)
      this.uniforms.set("u_Glow", shaderModule.weightSetting.value)
      this.uniforms.set("u_Fade", !shaderModule.fadeSetting.isAtMaximum)
   }
}
