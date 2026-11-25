package me.collective.client.render

import me.collective.client.utils.chat.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

interface ReloadableShaderPass {
    fun compileProgram(): CompiledShaderProgram

    fun reload(pass: BaseShaderPass) {
        var newProgram: CompiledShaderProgram? = null

        try {
            newProgram = compileProgram()
        } catch (failure: Throwable) {
            ShaderDiagnostics.logError(failure.message)
            val coloredNotification = Component.literal(RELOAD_FAILURE_MESSAGE).withStyle(ChatFormatting.RED)
            val signature = ChatUtils.createSignature(-99)
            ChatUtils.printChat(coloredNotification, signature, ChatUtils.MessagePriority.HIGH)
            try {
                val fallbackVertexRead = RenderTargetUtils.readShaderSource(pass.vertexResource)
                val fallbackFragmentRead = RenderTargetUtils.readShaderSource(pass.fragmentResource)
                newProgram = CompiledShaderProgram.compile(fallbackVertexRead, fallbackFragmentRead)
            } catch (fallbackFailure: Throwable) {
                ShaderDiagnostics.logError(fallbackFailure.message)
            }
        }

        if (newProgram == null) {
            if (pass.program == null) {
                throw IllegalStateException("Shader program compilation failed and no previous program is available.")
            }
            return
        }

        val oldProgram = pass.program
        pass.program = newProgram
        pass.uniforms = ShaderUniforms(newProgram.programId)
        oldProgram?.delete()
    }

    companion object {
        @JvmField
        val RELOAD_FAILURE_MESSAGE: String = "Failed to compile shader, check Minecraft logs for more details."
    }
}
