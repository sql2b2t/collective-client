package me.collective.client.render;

import me.collective.client.utils.inventory.NoOpVertexConsumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

public class DiscardingBufferSource extends BufferSource {
   public static final DiscardingBufferSource INSTANCE = new DiscardingBufferSource();

   @Override
   public void endBatch(RenderType renderType) {
   }

   private DiscardingBufferSource() {
      super(null, null);
   }

   @Override
   public void endBatch() {
   }

   @Override
   public VertexConsumer getBuffer(RenderType renderType) {
      return NoOpVertexConsumer.INSTANCE;
   }
}
