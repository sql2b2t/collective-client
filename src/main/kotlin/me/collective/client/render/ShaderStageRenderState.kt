package me.collective.client.render

import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL20C

object ShaderStageRenderState : RenderState {
    private var stage: ShaderStage? = null
    private var inputTextureId: Int = -1
    private var savedTextureBinding: Int = 0
    private var savedProgram: Int = 0
    private var savedMinFilter: Int = 0
    private var savedMagFilter: Int = 0
    private var savedWrapS: Int = 0
    private var savedWrapT: Int = 0

    fun configure(stage: ShaderStage, inputTextureId: Int) {
        this.stage = stage
        this.inputTextureId = inputTextureId
    }

    override fun apply(entering: Boolean) {
        if (entering) {
            savedProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)

            GL13C.glActiveTexture(GL13C.GL_TEXTURE15)

            savedTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)

            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, inputTextureId)
            savedMinFilter = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER)
            savedMagFilter = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER)
            savedWrapS = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S)
            savedWrapT = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE)

            val currentStage = stage!!
            val programId = currentStage.getProgram().programId
            GL20C.glUseProgram(programId)

            GL11C.glDisable(GL11C.GL_BLEND)
            currentStage.updateUniforms()
        } else {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE15)

            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, inputTextureId)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, savedMinFilter)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, savedMagFilter)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, savedWrapS)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, savedWrapT)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, savedTextureBinding)
            GL20C.glUseProgram(savedProgram)

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0)

            GL11C.glEnable(GL11C.GL_BLEND)
        }
    }
}
