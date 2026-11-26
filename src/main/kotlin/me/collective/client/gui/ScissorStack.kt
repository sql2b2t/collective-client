package me.collective.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.ScissorState
import me.collective.client.ClientAccess
import me.collective.client.render.GuiScaleUtils
import org.joml.Vector4d
import org.joml.Vector4i
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.max
import kotlin.math.min

object ScissorStack : ClientAccess {
    private val regions: Deque<ScissorRegion> = ArrayDeque()

    @JvmStatic
    fun pop() {
        getScissorState().disable()
        regions.removeLast()
        if (!regions.isEmpty()) {
            val previousRegion = regions.last
            val framebufferBounds = previousRegion.framebufferBounds
            enableFramebufferScissor(framebufferBounds.x, framebufferBounds.y, framebufferBounds.z, framebufferBounds.w)
        }
    }

    @JvmStatic
    fun push(x: Int, y: Int, width: Int, height: Int) {
        val region = ScissorRegion.create(x, y, width, height)
        val framebufferBounds = region.framebufferBounds
        enableFramebufferScissor(framebufferBounds.x, framebufferBounds.y, framebufferBounds.z, framebufferBounds.w)
        regions.addLast(region)
    }

    private fun getScissorState(): ScissorState {
        return RenderSystem.getScissorStateForRenderTypeDraws()
    }

    @JvmStatic
    fun pushIntersection(x: Int, y: Int, width: Int, height: Int) {
        val previousRegion = regions.peekLast()
        if (previousRegion != null) {
            val previousBounds = previousRegion.guiBounds
            val intersectionX = max(previousBounds.x, x)
            val intersectionY = max(previousBounds.y, y)
            val intersectionWidth = min(previousBounds.x + previousBounds.z, x + width) - intersectionX
            val intersectionHeight = min(previousBounds.y + previousBounds.w, y + height) - intersectionY
            push(intersectionX, intersectionY, intersectionWidth, intersectionHeight)
        } else {
            push(x, y, width, height)
        }
    }

    @JvmStatic
    fun enableFramebufferScissor(x: Int, y: Int, width: Int, height: Int) {
        getScissorState().enable(x, y, max(width, 0), max(height, 0))
    }

    @JvmRecord
    data class ScissorRegion(
        val guiBounds: Vector4i,
        val framebufferBounds: Vector4i
    ) {
        companion object {
            private val framebufferBoundsScratch = Vector4d()

            @JvmStatic
            fun create(x: Int, y: Int, width: Int, height: Int): ScissorRegion {
                val framebufferScale = ClientAccess.collectiveMinecraft.window.guiScale * GuiScaleUtils.getScaleMultiplier()
                val guiHeight = (ClientAccess.collectiveMinecraft.window.height / framebufferScale).toFloat()
                framebufferBoundsScratch.set(x.toDouble(), (guiHeight - y - height).toDouble(), width.toDouble(), height.toDouble())
                    .mul(framebufferScale)
                    .sub(0.0, 0.0, 0.0, 1.0)
                val guiBounds = Vector4i(x, y, width, height)
                val framebufferBounds = Vector4i(
                    framebufferBoundsScratch.x.toInt(),
                    framebufferBoundsScratch.y.toInt(),
                    framebufferBoundsScratch.z.toInt(),
                    framebufferBoundsScratch.w.toInt()
                )
                return ScissorRegion(guiBounds, framebufferBounds)
            }
        }
    }
}
