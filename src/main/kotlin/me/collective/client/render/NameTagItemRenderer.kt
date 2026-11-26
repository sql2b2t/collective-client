package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import me.collective.client.ClientAccess
import me.collective.client.render.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.rendertype.RenderType

object NameTagItemRenderer {
    private val submitNodes = SubmitNodeStorage()
    private val buffers = NameTagBufferSource()
    private val featureDispatcher: FeatureRenderDispatcher
        get() = ClientAccess.collectiveMinecraft.gameRenderer.featureRenderDispatcher()

    @JvmStatic
    fun getSubmitNodes(): SubmitNodeStorage = submitNodes

    @JvmStatic
    fun flush() {
        featureDispatcher.renderAllFeatures(submitNodes)
        buffers.endBatch()
    }

    private class NameTagBufferSource : BufferSource(
        ByteBufferBuilder(786432),
        Object2ObjectLinkedOpenHashMap()
    ) {
        override fun getBuffer(renderType: RenderType): VertexConsumer {
            val itemType = ClientRenderTypes.nameTagItem(renderType)
            return super.getBuffer(itemType)
        }
    }
}
