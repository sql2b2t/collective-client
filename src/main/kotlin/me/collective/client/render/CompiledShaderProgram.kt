package me.collective.client.render

import org.lwjgl.opengl.GL20

@JvmRecord
data class CompiledShaderProgram(val programId: Int) {

    fun delete() {
        GL20.glDeleteProgram(programId)
    }

    companion object {
        @JvmStatic
        fun fromResources(vertexResource: String, fragmentResource: String): CompiledShaderProgram {
            val vertexSource = RenderTargetUtils.readShaderSource(vertexResource)
            val fragmentSource = RenderTargetUtils.readShaderSource(fragmentResource)
            return compile(vertexSource, fragmentSource)
        }

        private fun compileShader(source: String, shaderType: Int): Int {
            val shaderId = GL20.glCreateShader(shaderType)
            val sourceBuilder = StringBuilder(source)
            if (shaderType == GL20.GL_FRAGMENT_SHADER) {
                var main = sourceBuilder.indexOf("void main(")
                if (main < 0) main = sourceBuilder.indexOf("void main (")
                if (main >= 0) {
                    var utilitySource = RenderTargetUtils.readShaderSource("util/util.frag")
                    if (source.contains("u_Overlay")) {
                        utilitySource += RenderTargetUtils.readShaderSource("util/blur_util.frag")
                    }
                    sourceBuilder.insert(main, "$utilitySource\n")
                }
            }
            GL20.glShaderSource(shaderId, sourceBuilder)
            GL20.glCompileShader(shaderId)
            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                val message = formatCompileFailure(sourceBuilder, shaderId)
                GL20.glDeleteShader(shaderId)
                throw ShaderCompilationException(message)
            }
            return shaderId
        }

        private fun formatCompileFailure(sourceBuilder: StringBuilder, shaderId: Int): String {
            val diagnosticSource = sourceBuilder.toString()
            val diagnostic = ShaderDiagnostics.formatCompileLog(diagnosticSource, shaderId)
            return "Failed to compile shader:\n$diagnostic"
        }

        private fun linkProgram(programId: Int, vertexShader: Int, fragmentShader: Int) {
            GL20.glAttachShader(programId, vertexShader)
            GL20.glAttachShader(programId, fragmentShader)
            GL20.glLinkProgram(programId)
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
                throw ShaderCompilationException("Failed to link shader program:\n" + GL20.glGetProgramInfoLog(programId))
            }
            GL20.glDeleteShader(vertexShader)
            GL20.glDeleteShader(fragmentShader)
        }

        @JvmStatic
        fun compile(vertexSource: String, fragmentSource: String): CompiledShaderProgram {
            val programId = GL20.glCreateProgram()
            if (programId == 0) {
                throw RuntimeException("Failed to create program.")
            }

            var vertexShader = -1
            var fragmentShader = -1
            try {
                vertexShader = compileShader(vertexSource, GL20.GL_VERTEX_SHADER)
                fragmentShader = compileShader(fragmentSource, GL20.GL_FRAGMENT_SHADER)
                linkProgram(programId, vertexShader, fragmentShader)
            } catch (failure: Throwable) {
                if (vertexShader != -1) {
                    GL20.glDeleteShader(vertexShader)
                }
                if (fragmentShader != -1) {
                    GL20.glDeleteShader(fragmentShader)
                }
                GL20.glDeleteProgram(programId)
                throw failure
            }
            return CompiledShaderProgram(programId)
        }
    }
}
