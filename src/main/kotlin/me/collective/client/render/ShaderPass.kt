package me.collective.client.render

interface ShaderPass {
    fun getProgram(): CompiledShaderProgram?

    fun updateUniforms()

    fun render(draw: Runnable)

    companion object {
        @JvmField
        val START_TIME_MILLIS: Long = System.currentTimeMillis()
    }
}
