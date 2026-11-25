package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.collective.client.utils.inventory.NoOpVertexConsumer
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.RenderType

class ChamsBufferSource internal constructor() :
    BufferSource(ByteBufferBuilder(0), Object2ObjectLinkedOpenHashMap()) {
    private val consumersByRenderType = Object2ObjectOpenHashMap<RenderType, VertexConsumer>()

    fun clearConsumers() {
        consumersByRenderType.clear()
    }

    override fun getBuffer(renderType: RenderType): VertexConsumer {
        return if (renderType.toString().contains("item_entity_translucent_cull")) {
            NoOpVertexConsumer.INSTANCE
        } else {
            consumersByRenderType.computeIfAbsent(renderType) { ChamsVertexConsumer() }
        }
    }

    companion object {
        @JvmField
        val INSTANCE: ChamsBufferSource = ChamsBufferSource()
    }
}
