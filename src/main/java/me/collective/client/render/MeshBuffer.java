package me.collective.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.rendertype.RenderType;

public class MeshBuffer implements VertexConsumer {
   private final ByteBufferBuilder byteBuffer;
   protected BufferBuilder builder;
   private Mode mode;
   private VertexFormat format;

   public static MeshBuffer create(int capacity) {
      return new MeshBuffer(capacity);
   }

   public BufferBuilder getBuilder() {
      if (this.builder == null) {
         if (this.mode == null
            || this.format == null) {
            throw new IllegalArgumentException("Tried to invoke empty not caching buffer");
         }

         this.builder = new BufferBuilder(
            this.byteBuffer,
            this.mode,
            this.format
         );
      }

      return this.builder;
   }

   public boolean isBuilding() {
      return this.builder != null;
   }

   public MeshData build() {
      if (this.builder == null) {
         throw new IllegalArgumentException("Tried to end empty buffer");
      }

      MeshData mesh = this.builder.build();
      this.builder = null;
      return mesh;
   }

   public static MeshBuffer create() {
      return create(786432);
   }

   @Override
   public VertexConsumer setNormal(float x, float y, float z) {
      return this.getBuilder().setNormal(x, y, z);
   }

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
      return this.getBuilder().addVertex(x, y, z);
   }

   @Override
   public VertexConsumer setLineWidth(float lineWidth) {
      return this.getBuilder().setLineWidth(lineWidth);
   }

   @Override
   public VertexConsumer setColor(int color) {
      return this.getBuilder().setColor(color);
   }

   @Override
   public VertexConsumer setUv(float u, float v) {
      return this.getBuilder().setUv(u, v);
   }

   protected MeshBuffer(int capacity) {
      this.byteBuffer = new ByteBufferBuilder(capacity);
   }

   public boolean isEmpty() {
      return this.builder == null;
   }

   public MeshBuffer setFormat(
      Mode mode, VertexFormat format
   ) {
      this.mode = mode;
      this.format = format;
      return this;
   }

   public static void draw(BufferBuilder builder) {
      if (builder != null) {
         MeshData mesh = builder.build();
         if (mesh != null) {
            MeshDrawContext.draw(mesh);
         }
      }
   }

   public static void draw(MeshBuffer buffer) {
      if (buffer.isBuilding()) {
         draw(
            buffer.builder
         );
         buffer.builder = null;
      }
   }

   public BufferBuilder begin(Mode mode, VertexFormat format) {
      this.builder = new BufferBuilder(
         this.byteBuffer, mode, format
      );
      return this.builder;
   }

   public void draw(RenderType renderType) {
      if (this.builder != null) {
         MeshData mesh = this.builder.build();
         this.builder = null;
         if (mesh != null) {
            renderType.draw(mesh);
         }
      }
   }

   @Override
   public VertexConsumer setUv1(int overlayU, int overlayV) {
      return this.getBuilder().setUv1(overlayU, overlayV);
   }

   @Override
   public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      return this.getBuilder().setColor(red, green, blue, alpha);
   }

   @Override
   public VertexConsumer setUv2(int lightU, int lightV) {
      return this.getBuilder().setUv2(lightU, lightV);
   }

   public void draw() {
      if (this.builder != null) {
         draw(
            this.builder
         );
         this.builder = null;
      }
   }

}
