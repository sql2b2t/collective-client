package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import me.collective.client.utils.inventory.NoOpVertexConsumer
import me.collective.client.render.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.rendertype.RenderType

object DiscardingBufferSource : BufferSource(ByteBufferBuilder(0), Object2ObjectLinkedOpenHashMap()) {
   override fun endBatch(renderType: RenderType) {
   }

   override fun endBatch() {
   }

   override fun getBuffer(renderType: RenderType): VertexConsumer {
      return NoOpVertexConsumer.INSTANCE
   }
}
