package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.RenderType

object NameTagItemRenderer {
    private val buffers = NameTagBufferSource()

    @JvmStatic
    fun getBufferSource(): MultiBufferSource = buffers

    @JvmStatic
    fun flush() {
        buffers.endBatch()
    }

    private class NameTagBufferSource : BufferSource(
        ByteBufferBuilder(786432),
        Object2ObjectLinkedOpenHashMap()
    ) {
        override fun getBuffer(renderType: RenderType): VertexConsumer {
            val itemType = ClientRenderTypes.nameTagItem(renderType)
            if (ClientRenderPipelines.isGlint(itemType)) {
                this.fixedBuffers.computeIfAbsent(itemType) { type -> ByteBufferBuilder(type.bufferSize()) }
            }
            return super.getBuffer(itemType)
        }
    }
}
