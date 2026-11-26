package me.collective.client.render

import org.lwjgl.opengl.GL20

open class BaseShaderPass(
    @JvmField internal val vertexResource: String,
    @JvmField internal val fragmentResource: String
) : ShaderPass, ReloadableShaderPass {
    @JvmField
    internal var program: CompiledShaderProgram? = null

    internal lateinit var uniforms: ShaderUniforms

    override fun getProgram(): CompiledShaderProgram? = program

    override fun updateUniforms() {
    }

    protected open fun initializeProgram() {
        val compiled = compileProgram()
        program = compiled
        uniforms = ShaderUniforms(compiled.programId)
    }

    override fun compileProgram(): CompiledShaderProgram {
        return CompiledShaderProgram.fromResources(vertexResource, fragmentResource)
    }

    override fun render(draw: Runnable) {
        val programId = program!!.programId
        GL20.glUseProgram(programId)
        draw.run()
    }
}
