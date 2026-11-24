package me.collective.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class MeshDrawContext {
   private static final MeshBuffer SHARED_BUFFER = MeshBuffer.create();
   private static RenderType currentRenderType;

   public static RenderType getRenderType() {
      return currentRenderType;
   }

   public static void draw(MeshData mesh) {
      RenderSystem.assertOnRenderThread();
      getRenderType().draw(mesh);
   }

   public static void setRenderType(RenderType renderType) {
      currentRenderType = renderType;
   }

   public static void draw(BufferBuilder builder) {
      draw(builder.buildOrThrow());
   }

   public static MeshBuffer getBuffer() {
      return SHARED_BUFFER;
   }
}
