package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import me.collective.client.utils.inventory.NoOpVertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.OutlineBufferSource
import net.minecraft.client.renderer.RenderType

class NoOpOutlineBufferSource private constructor() :
    OutlineBufferSource(MultiBufferSource.immediateWithBuffers(Object2ObjectLinkedOpenHashMap(), ByteBufferBuilder(0))) {
    override fun endOutlineBatch() {
    }

    override fun getBuffer(renderType: RenderType): VertexConsumer = NoOpVertexConsumer.INSTANCE

    companion object {
        @JvmField
        val INSTANCE: NoOpOutlineBufferSource = NoOpOutlineBufferSource()
    }
}
