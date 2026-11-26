package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.collective.client.utils.inventory.NoOpVertexConsumer
import me.collective.client.render.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderType

class ChamsBufferSource internal constructor() :
    BufferSource(ByteBufferBuilder(0), Object2ObjectLinkedOpenHashMap()) {
    private val consumersByRenderType = Object2ObjectOpenHashMap<RenderType, VertexConsumer>()

    fun clearConsumers() {
        consumersByRenderType.clear()
    }

    override fun getBuffer(renderType: RenderType): VertexConsumer {
        return if (renderType.state.outputTarget == OutputTarget.ITEM_ENTITY_TARGET) {
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
