package me.collective.client.render

import me.collective.client.ClientContext
import me.collective.client.managers.ConfigurationManager
import me.collective.client.utils.UncheckedExceptions
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.awt.Color
import java.nio.file.Files
import java.util.regex.Matcher
import java.util.regex.Pattern

open class CustomShaderPass : PreprocessedShaderPass("base.vert", "custom.frag", COVERAGE_PASS), ReloadableShaderPass {
   init {
      this.reload(this)
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

      val elapsedMillis = (System.currentTimeMillis() - ShaderPass.START_TIME_MILLIS).toFloat()
      this.uniforms.set("u_Time", elapsedMillis / 1000.0f)
      this.uniforms.set("u_Fade", !shaderModule.fadeSetting.isAtMaximum)
   }

   override fun compileProgram(): CompiledShaderProgram {
      val shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY
      val customFragmentPath = shaderDirectory.resolve("custom.frag")
      val vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource)
      var fragmentSource = RenderTargetUtils.readShaderSource(this.fragmentResource)

      val customMainImage = try {
         Files.readString(customFragmentPath)
      } catch (failure: Throwable) {
         throw UncheckedExceptions.rethrow<Throwable>(failure)
      }

      fragmentSource = MAIN_IMAGE_PATTERN.matcher(fragmentSource)
         .replaceFirst(Matcher.quoteReplacement(customMainImage))

      return CompiledShaderProgram.compile(vertexSource, fragmentSource)
   }

   companion object {
      private val MAIN_IMAGE_PATTERN: Pattern =
         Pattern.compile("vec4\\s+mainImage\\s*\\(\\s*bool\\s+outline\\s*\\)\\s*\\{[\\s\\S]*?\\}")
   }
}
