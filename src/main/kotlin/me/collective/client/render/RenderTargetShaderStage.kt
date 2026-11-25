package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import me.collective.client.ClientAccess
import me.collective.client.managers.RenderStateManager
import org.lwjgl.glfw.GLFW

open class RenderTargetShaderStage(
    @JvmField protected val program: CompiledShaderProgram,
    @JvmField protected val outputTarget: RenderTarget
) : ClientAccess, ShaderStage {
    @JvmField
    protected val uniforms: ShaderUniforms = ShaderUniforms(program.programId)

    constructor(vertexResource: String, fragmentResource: String, outputTarget: RenderTarget) : this(
        CompiledShaderProgram.compile(
            RenderTargetUtils.readShaderSource(vertexResource),
            RenderTargetUtils.readShaderSource(fragmentResource)
        ),
        outputTarget
    )

    override fun getProgram(): CompiledShaderProgram = program

    override fun updateUniforms() {
        uniforms.set("u_Proj", RenderUtils.WORLD_PROJECTION_MATRIX)
        uniforms.set("u_ModelView", RenderSystem.getModelViewStack())
        uniforms.set("u_Size", outputTarget.width.toFloat(), outputTarget.height.toFloat())
        uniforms.set("u_Texture", 15)
        uniforms.set("u_Time", GLFW.glfwGetTime().toFloat())
    }

    override fun getOutputTarget(): RenderTarget = outputTarget

    protected open fun drawFullscreenQuad() {
        val capturedTesselator = Tesselator.getInstance()
        val vertices = capturedTesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        vertices.addVertex(-1.0f, -1.0f, 0.0f)
        vertices.addVertex(-1.0f, 1.0f, 0.0f)
        vertices.addVertex(1.0f, 1.0f, 0.0f)
        vertices.addVertex(1.0f, -1.0f, 0.0f)
        val renderType = ClientRenderTypes.entityShader()
        val mesh = vertices.buildOrThrow()
        renderType.draw(mesh)
    }

    override fun apply(inputTarget: RenderTarget) {
        RenderUtils.clearColor(outputTarget)
        RenderUtils.clearDepth(outputTarget)
        RenderTargetUtils.overrideRenderTargets(outputTarget)
        try {
            val inputTextureId = inputTarget.colorTextureId
            ShaderStageRenderState.configure(this, inputTextureId)
            RenderStateManager.runWithStates(::drawFullscreenQuad, ShaderStageRenderState)
        } finally {
            RenderTargetUtils.restoreRenderTargets()
        }
    }
}
