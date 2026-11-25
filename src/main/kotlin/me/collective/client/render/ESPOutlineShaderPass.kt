package me.collective.client.render

import me.collective.client.ClientContext
import me.collective.client.modules.render.ESPModule

open class ESPOutlineShaderPass : PostProcessShaderPass("base.vert", "outline.frag") {
   init {
      this.initializeProgram()
   }

   override fun updateUniforms() {
      super.updateUniforms()
      val espModule = espModule() ?: return
      this.uniforms.set("u_Width", 2)
      this.uniforms.set("u_GlowMultiplier", 1.0f)
      this.uniforms.set("u_FillOpacity", espModule.blockFillOpacitySetting.value)
   }

   companion object {
      private var espModule: ESPModule? = null

      private fun espModule(): ESPModule? {
         if (espModule == null) {
            espModule = ClientContext.getModule(ESPModule::class.java)
         }
         return espModule
      }
   }
}
