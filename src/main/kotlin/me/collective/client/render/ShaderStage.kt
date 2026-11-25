package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget

interface ShaderStage {
    fun getProgram(): CompiledShaderProgram

    fun updateUniforms()

    fun getOutputTarget(): RenderTarget

    fun apply(inputTarget: RenderTarget)
}
