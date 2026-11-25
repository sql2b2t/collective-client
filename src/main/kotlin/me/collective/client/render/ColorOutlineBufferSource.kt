package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import java.util.function.Supplier
import me.collective.client.utils.inventory.NoOpVertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.RenderType

open class ColorOutlineBufferSource : MultiBufferSource {
    private val buffers: BufferSource = MultiBufferSource.immediate(ByteBufferBuilder(1536))
    private var colorArgb: Int = 0
    private var colorSet: Boolean = false

    fun getColorArgb(): Int = colorArgb

    fun endBatchAndClean() {
        buffers.endBatch()
        BufferSourceUtils.cleanup(buffers)
    }

    fun reset() {
        colorSet = false
    }

    fun hasColor(): Boolean = colorSet

    override fun getBuffer(renderType: RenderType): VertexConsumer {
        if (renderType.isOutline) {
            return OutlineVertexConsumer(this, buffers.getBuffer(renderType))
        }

        val outlineType = renderType.outline()
        return if (outlineType.isPresent) {
            OutlineVertexConsumer(this, buffers.getBuffer(outlineType.get()))
        } else {
            NoOpVertexConsumer.INSTANCE
        }
    }

    fun setColorArgb(argb: Int) {
        colorArgb = argb
        colorSet = true
    }

    open class SuppliedBufferSource(
        private val sourceSupplier: Supplier<out MultiBufferSource>
    ) : BufferSource(ByteBufferBuilder(0), Object2ObjectLinkedOpenHashMap()) {
        override fun getBuffer(renderType: RenderType): VertexConsumer =
            sourceSupplier.get().getBuffer(renderType)

        override fun endBatch() {
        }

        override fun endBatch(renderType: RenderType) {
        }
    }

    private class OutlineVertexConsumer(
        private val source: ColorOutlineBufferSource,
        private val delegate: VertexConsumer
    ) : VertexConsumer {
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
            delegate.addVertex(x, y, z).setColor(source.getColorArgb())
            return this
        }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this

        override fun setColor(argb: Int): VertexConsumer = this

        override fun setUv(u: Float, v: Float): VertexConsumer {
            delegate.setUv(u, v)
            return this
        }

        override fun setUv1(u: Int, v: Int): VertexConsumer = this

        override fun setUv2(u: Int, v: Int): VertexConsumer = this

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
    }
}
