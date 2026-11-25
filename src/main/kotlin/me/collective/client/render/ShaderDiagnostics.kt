package me.collective.client.render

import me.collective.client.ClientAccess
import org.lwjgl.opengl.GL20
import org.slf4j.MarkerFactory
import java.util.regex.Pattern

object ShaderDiagnostics : ClientAccess {
    private val SOURCE_LINE_PATTERN: Pattern = Pattern.compile("0\\((\\d+)\\)")

    @JvmStatic
    fun formatCompileLog(source: String, shaderId: Int): String {
        val sourceLines = source.split("\n")
        val diagnostics = StringBuilder()
        val driverLog = GL20.glGetShaderInfoLog(shaderId)
        if (driverLog.isEmpty()) {
            return "No log provided by driver."
        }

        for (logLine in driverLog.split("\n")) {
            val lineMatcher = SOURCE_LINE_PATTERN.matcher(logLine)
            if (!lineMatcher.find()) {
                diagnostics.append("[INTERNAL] ").append(logLine).append("\n")
            } else {
                val lineNumber = lineMatcher.group(1)
                try {
                    val sourceLineIndex = lineNumber.toInt() - 1
                    if (sourceLineIndex in sourceLines.indices) {
                        val trimmedSourceLine = sourceLines[sourceLineIndex].trim()
                        if (trimmedSourceLine.isNotEmpty()) {
                            diagnostics.append("Line ").append(lineNumber).append(": ").append(trimmedSourceLine).append("\n")
                            val indicator = "^".repeat(trimmedSourceLine.length)
                            diagnostics.append(indicator).append("\n")
                        }
                    }
                    diagnostics.append(logLine).append("\n\n")
                } catch (_: NumberFormatException) {
                    diagnostics.append(logLine).append("\n")
                }
            }
        }
        return diagnostics.toString()
    }

    @JvmStatic
    fun logError(message: String?) {
        val logger = ClientAccess.logger
        val marker = MarkerFactory.getMarker("Shaders")
        logger.error(marker, message)
    }
}
