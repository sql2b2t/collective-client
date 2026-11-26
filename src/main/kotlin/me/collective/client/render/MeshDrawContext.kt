package me.collective.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import net.minecraft.client.renderer.rendertype.RenderType

object MeshDrawContext {
    private val SHARED_BUFFER: MeshBuffer = MeshBuffer.create()
    private var currentRenderType: RenderType? = null

    @JvmStatic
    fun getRenderType(): RenderType = currentRenderType!!

    @JvmStatic
    fun draw(mesh: MeshData) {
        RenderSystem.assertOnRenderThread()
        MeshBuffer.drawMesh(getRenderType(), mesh)
    }

    @JvmStatic
    fun setRenderType(renderType: RenderType?) {
        currentRenderType = renderType
    }

    @JvmStatic
    fun draw(builder: BufferBuilder) {
        draw(builder.buildOrThrow())
    }

    @JvmStatic
    fun getBuffer(): MeshBuffer = SHARED_BUFFER
}
