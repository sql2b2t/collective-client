package me.collective.client.render

import com.mojang.blaze3d.vertex.VertexFormat
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter
import org.lwjgl.system.MemoryStack

open class SodiumMeshBuffer(capacity: Int) : MeshBuffer(capacity), VertexBufferWriter {
    override fun push(stack: MemoryStack, vertices: Long, vertexCount: Int, format: VertexFormat) {
    }

    override fun canUseIntrinsics(): Boolean = false
}
