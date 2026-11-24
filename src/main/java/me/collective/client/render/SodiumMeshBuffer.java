package me.collective.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

public class SodiumMeshBuffer extends MeshBuffer implements VertexBufferWriter {
   public SodiumMeshBuffer(int capacity) {
      super(capacity);
   }

   @Override
   public void push(MemoryStack stack, long vertices, int vertexCount, VertexFormat format) {

   }

   @Override
   public boolean canUseIntrinsics() {
      return false;
   }

}
