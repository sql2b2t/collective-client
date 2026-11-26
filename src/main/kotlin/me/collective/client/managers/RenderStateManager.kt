package me.collective.client.managers

import com.mojang.blaze3d.opengl.GlStateManager
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.collective.client.render.RenderState
import org.lwjgl.opengl.GL20C
import org.lwjgl.opengl.GL32C

object RenderStateManager {
    private val registeredStates: MutableList<RenderState> = ObjectArrayList()

    @JvmField
    val BLENDED_SMOOTH_LINES: RenderState = RenderState { entering ->
        if (entering) {
            GlStateManager._enableBlend(0)
            GL32C.glEnable(GL32C.GL_LINE_SMOOTH)
        } else {
            GL32C.glDisable(GL32C.GL_LINE_SMOOTH)
            GlStateManager._disableBlend(0)
        }
    }

    @JvmField
    val THROUGH_WALLS: RenderState = RenderState { entering ->
        if (entering) {
            GlStateManager._depthMask(false)
            GlStateManager._disableDepthTest()
            GL20C.glDepthRange(0.0, 0.1)
        } else {
            GlStateManager._depthMask(true)
            GlStateManager._enableDepthTest()
            GL20C.glDepthRange(0.0, 1.0)
        }
    }

    @JvmField
    val POLYGON_OFFSET: RenderState = RenderState { entering ->
        if (entering) {
            GlStateManager._enablePolygonOffset()
            GlStateManager._polygonOffset(1.0f, -500000.0f)
            GlStateManager._enableBlend(0)
            GlStateManager._disableCull()
        } else {
            GlStateManager._polygonOffset(1.0f, 500000.0f)
            GlStateManager._disableBlend(0)
            GlStateManager._enableCull()
            GlStateManager._disablePolygonOffset()
        }
    }

    @JvmField
    val POLYGON_OFFSET_THROUGH_WALLS: RenderState = POLYGON_OFFSET.then(THROUGH_WALLS)

    private var active: Boolean = false

    @JvmStatic
    fun reset() {
        registeredStates.clear()
        active = false
    }

    @JvmStatic
    fun runWithStates(draw: Runnable, vararg states: RenderState) {
        addStates(*states)
        try {
            draw.run()
        } finally {
            reset()
        }
    }

    @JvmStatic
    fun addStates(vararg states: RenderState) {
        registeredStates.addAll(states)
        active = true
    }

    @JvmStatic
    fun applyRegistered(entering: Boolean) {
        if (active) {
            for (state in registeredStates) {
                state.apply(entering)
            }
        }
    }

    @JvmStatic
    fun isActive(): Boolean {
        return active
    }
}
