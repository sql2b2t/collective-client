package me.collective.client.managers

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.collective.client.ClientAccess
import me.collective.client.gui.components.ColorSubmitNodeStorage
import me.collective.client.render.BaseShaderPass
import me.collective.client.render.BloomMaskPipeline
import me.collective.client.render.BloomShaderPass
import me.collective.client.render.BlurMaskPipeline
import me.collective.client.render.ColorOutlineBufferSource
import me.collective.client.render.CustomShaderPass
import me.collective.client.render.DepthOfFieldPipeline
import me.collective.client.render.DiscardingBufferSource
import me.collective.client.render.ESPOutlineShaderPass
import me.collective.client.render.GlowyShaderPipeline
import me.collective.client.render.GradientShaderPass
import me.collective.client.render.NoOpOutlineBufferSource
import me.collective.client.render.RainbowShaderPass
import me.collective.client.render.ReloadableShaderPass
import me.collective.client.render.ShaderPass
import me.collective.client.render.SkyboxShaderPass
import me.collective.client.render.SolidShaderPass
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.resources.model.sprite.AtlasManager

class ShaderRegistry : ClientAccess {
   private val passes = Object2ObjectOpenHashMap<String, ShaderPass>()

   @JvmField
   var renderingIntoTarget: Boolean = false

   @JvmField
   var renderingHands: Boolean = false

   init {
      this.register("outline", ESPOutlineShaderPass())
      this.register("solid", SolidShaderPass())
      this.register("rainbow", RainbowShaderPass())
      this.register("gradient", GradientShaderPass())
      this.register("bloom", BloomShaderPass())
      this.register("custom", CustomShaderPass())
      this.register("sky", SkyboxShaderPass())
      this.register("dof", DepthOfFieldPipeline())
      this.register("blur_mask", BlurMaskPipeline())
      this.register("bloom_mask", BloomMaskPipeline())
      this.register("glowy", GlowyShaderPipeline())
   }

   fun getPass(id: String): ShaderPass = this.passes.getValue(id)

   fun reloadAll() {
      for (pass in this.passes.values) {
         if (pass is BaseShaderPass && pass is ReloadableShaderPass) {
            pass.reload(pass)
         }
      }
   }

   private fun register(id: String, pass: ShaderPass) {
      this.passes[id] = pass
   }

   fun render(id: String, draw: Runnable) {
      this.getPass(id).render(draw)
   }

   companion object {
      private val submitNodes = ColorSubmitNodeStorage()
      private var featureDispatcher: FeatureRenderDispatcher? = null
      private var bufferSource: ColorOutlineBufferSource? = null

      private fun getFeatureDispatcher(): FeatureRenderDispatcher {
         if (featureDispatcher == null) {
            featureDispatcher = ClientAccess.collectiveMinecraft.gameRenderer.featureRenderDispatcher()
         }
         return featureDispatcher!!
      }

      private fun readAtlasManager(): AtlasManager =
         ClientAccess.collectiveMinecraft.getAtlasManager()

      @JvmStatic
      fun getSubmitNodes(): ColorSubmitNodeStorage = submitNodes

      @JvmStatic
      fun getBufferSource(): ColorOutlineBufferSource {
         if (bufferSource == null) {
            bufferSource = ColorOutlineBufferSource()
         }
         return bufferSource!!
      }

      @JvmStatic
      fun finishFeatureRendering() {
         val dispatcher = getFeatureDispatcher()
         dispatcher.renderAllFeatures(submitNodes)
      }

      @JvmStatic
      fun setRenderColor(color: Int) {
         getBufferSource().setColorArgb(color)
         getSubmitNodes().setColorArgb(color)
      }
   }
}
