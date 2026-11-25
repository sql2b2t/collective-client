package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import me.collective.client.ClientContext
import me.collective.client.managers.RenderTargetRegistry
import me.collective.client.modules.render.ShaderModule

abstract class PreprocessedShaderPass internal constructor(
    vertexResource: String,
    fragmentResource: String,
    private val prePass: PrePass?
) : PostProcessShaderPass(vertexResource, fragmentResource) {

    override fun prepareOutput() {
        val shaderModule = shaderModule() ?: return
        if (!shaderModule.shouldSkipHandOutline() && shaderModule.lineWidthSetting.getValue() > 0 && prePass != null) {
            prePass.apply(POST_TARGET)
        }
    }

    override fun updateUniforms() {
        super.updateUniforms()
        if (prePass != null) {
            uniforms.set(prePass.samplerName, 16, PRE_TARGET)
        }
    }

    internal class PrePass internal constructor(
        fragmentResource: String,
        internal val samplerName: String,
        private val coverage: Boolean
    ) : RenderTargetShaderStage("base.vert", fragmentResource, PRE_TARGET) {

        override fun updateUniforms() {
            super.updateUniforms()
            val shaderModule = shaderModule() ?: return
            uniforms.set("u_Width", shaderModule.lineWidthSetting.getValue())
            if (coverage) {
                uniforms.set("u_Step", shaderModule.qualitySetting.getValue())
            }
        }
    }

    companion object {
        private var shaderModule: ShaderModule? = null

        @JvmStatic
        protected fun shaderModule(): ShaderModule? {
            if (shaderModule == null) {
                shaderModule = ClientContext.getModule(ShaderModule::class.java)
            }
            return shaderModule
        }

        private val PRE_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("collective_pre_pass")

        @JvmField
        internal val COVERAGE_PASS: PrePass = PrePass("multi/coverage.frag", "u_Coverage", true)

        @JvmField
        internal val DISTANCE_PASS: PrePass = PrePass("multi/distance.frag", "u_Distance", false)
    }
}
