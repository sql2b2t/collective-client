package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.textures.FilterMode
import me.collective.client.ClientAccess

open class ShaderPipeline(
    @JvmField protected val sourceTarget: RenderTarget
) : ClientAccess, ShaderPass {
    @JvmField
    protected val stages: MutableList<ShaderStage> = ArrayList()
    private var blendOutput: Boolean = false
    private var outputFilter: FilterMode = FilterMode.NEAREST

    override fun getProgram(): CompiledShaderProgram? {
        return stages.firstOrNull()?.getProgram()
    }

    override fun updateUniforms() {
    }

    protected fun enableOutputBlending() {
        blendOutput = true
    }

    protected fun enableLinearOutputFiltering() {
        outputFilter = FilterMode.LINEAR
    }

    open fun addStage(stage: ShaderStage) {
        stages.add(stage)
    }

    override fun render(ignoredDraw: Runnable) {
        if (stages.isEmpty()) {
            return
        }
        var currentTarget = sourceTarget

        for (stage in stages) {
            stage.apply(currentTarget)
            currentTarget = stage.getOutputTarget()
        }

        RenderUtils.blitToMain(
            currentTarget,
            if (blendOutput) ClientRenderPipelines.BLIT_TRANSLUCENT else ClientRenderPipelines.BLIT_NO_BLEND,
            outputFilter
        )
    }
}
