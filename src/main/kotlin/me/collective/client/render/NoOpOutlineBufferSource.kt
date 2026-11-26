package me.collective.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import me.collective.client.utils.inventory.NoOpVertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

class NoOpOutlineBufferSource private constructor() : MultiBufferSource {
    fun endOutlineBatch() {
    }

    override fun getBuffer(renderType: RenderType): VertexConsumer = NoOpVertexConsumer.INSTANCE

    companion object {
        @JvmField
        val INSTANCE: NoOpOutlineBufferSource = NoOpOutlineBufferSource()
    }
}
