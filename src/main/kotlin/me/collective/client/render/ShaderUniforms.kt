package me.collective.client.render

import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.pipeline.RenderTarget
import it.unimi.dsi.fastutil.objects.Object2IntFunction
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import java.awt.Color
import java.nio.FloatBuffer

open class ShaderUniforms(private val programId: Int) {
    private val matrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private val locations: Object2IntMap<String> = Object2IntOpenHashMap()

    fun set(name: String, textureUnit: Int, target: RenderTarget) {
        val activeTexture = GL13.GL_TEXTURE0 + textureUnit
        GL13.glActiveTexture(activeTexture)
        val colorTexture = target.colorTexture as GlTexture
        val textureId = colorTexture.glId()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        set(name, textureUnit)
    }

    fun set(name: String, matrix: Matrix4f) {
        matrixBuffer.rewind()
        matrix.get(matrixBuffer)
        matrixBuffer.rewind()
        val location = getLocation(name)
        GL20.glUniformMatrix4fv(location, false, matrixBuffer)
    }

    fun set(name: String, value: Float) {
        val location = getLocation(name)
        GL20.glUniform1f(location, value)
    }

    fun set(name: String, x: Float, y: Float) {
        val location = getLocation(name)
        GL20.glUniform2f(location, x, y)
    }

    fun set(name: String, value: Int) {
        val location = getLocation(name)
        GL20.glUniform1i(location, value)
    }

    fun set(name: String, enabled: Boolean) {
        val location = getLocation(name)
        val booleanValue = if (enabled) 1 else 0
        GL20.glUniform1i(location, booleanValue)
    }

    fun set(name: String, color: Color) {
        val location = getLocation(name)
        val normalizedRed = color.red / 255.0f
        val normalizedGreen = color.green / 255.0f
        val normalizedBlue = color.blue / 255.0f
        val alpha = color.alpha / 255.0f
        GL20.glUniform4f(location, normalizedRed, normalizedGreen, normalizedBlue, alpha)
    }

    private fun getLocation(name: String): Int {
        locations.computeIfAbsent(name, Object2IntFunction { GL20.glGetUniformLocation(programId, name) })
        return locations.getInt(name)
    }
}
