package me.collective.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import me.collective.client.modules.render.ChamsModule;

public class ChamsVertexConsumer implements VertexConsumer {
   static float offsetX;
   static float offsetY;
   static float offsetZ;
   private final float[] quadX = new float[4];
   private final float[] quadY = new float[4];
   private final float[] quadZ = new float[4];
   private final float[] quadU = new float[4];
   private final float[] quadV = new float[4];
   private int vertexCount = 0;

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
      if (this.vertexCount >= 4) {
         this.vertexCount = 0;
      }

      quadX[this.vertexCount] = x;
      quadY[this.vertexCount] = y;
      quadZ[this.vertexCount] = z;
      this.vertexCount++;
      if (ChamsModule.renderingShineModels) {
         return this;
      }

      if (this.vertexCount == 4) {
         if (EntityChamsRenderer.hasVisibleFill()) {
            for (int vertex = 0; vertex < 4; vertex++) {
               EntityChamsRenderer.fillMesh
                  .addVertex(offsetX + quadX[vertex], offsetY + quadY[vertex], offsetZ + quadZ[vertex])
                  .setColor(EntityChamsRenderer.fillColor.hashCode());
            }
         }

         if (EntityChamsRenderer.hasVisibleOutline()) {
            this.emitOutlineSegment(
               offsetX + quadX[0], offsetY + quadY[0], offsetZ + quadZ[0],
               offsetX + quadX[1], offsetY + quadY[1], offsetZ + quadZ[1]
            );
            this.emitOutlineSegment(
               offsetX + quadX[1], offsetY + quadY[1], offsetZ + quadZ[1],
               offsetX + quadX[2], offsetY + quadY[2], offsetZ + quadZ[2]
            );
            this.emitOutlineSegment(
               offsetX + quadX[2], offsetY + quadY[2], offsetZ + quadZ[2],
               offsetX + quadX[3], offsetY + quadY[3], offsetZ + quadZ[3]
            );

            this.emitOutlineSegment(
               offsetX + quadX[3], offsetY + quadY[3], offsetZ + quadZ[3],
               offsetX + quadX[0], offsetY + quadY[0], offsetZ + quadZ[0]
            );
         }

         this.vertexCount = 0;
      }

      return this;
   }

   @Override
   public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      return this;
   }

   @Override
   public VertexConsumer setColor(int argb) {
      return this;
   }

   @Override
   public VertexConsumer setUv(float u, float v) {
      if (!ChamsModule.renderingShineModels) {
         return this;
      }

      if (this.vertexCount == 0) {
         return this;
      }

      quadU[this.vertexCount - 1] = u;
      quadV[this.vertexCount - 1] = v;
      if (this.vertexCount == 4) {
         this.emitTexturedQuad(EntityChamsRenderer.relation.getMeshBuffer());
         this.vertexCount = 0;
      }

      return this;
   }

   @Override
   public VertexConsumer setUv1(int u, int v) {
      return this;
   }

   @Override
   public VertexConsumer setUv2(int u, int v) {
      return this;
   }

   @Override
   public VertexConsumer setNormal(float x, float y, float z) {
      return this;
   }

   @Override
   public VertexConsumer setLineWidth(float width) {
      return this;
   }

   private void emitTexturedQuad(VertexConsumer consumer) {
      for (int vertex = 0; vertex < 4; vertex++) {
         consumer.addVertex(offsetX + quadX[vertex], offsetY + quadY[vertex], offsetZ + quadZ[vertex])
            .setUv(quadU[vertex], quadV[vertex]);
      }
   }

   public void emitOutlineSegment(
      float startX, float startY, float startZ, float endX, float endY, float endZ
   ) {
      float deltaX = endX - startX;
      float deltaY = endY - startY;
      float deltaZ = endZ - startZ;
      float length = Mth.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
      if (length < 1.0E-6F) {
         return;
      }
      float normalX = deltaX / length;
      float normalY = deltaY / length;
      float normalZ = deltaZ / length;
      EntityChamsRenderer.outlineMesh.addVertex(startX, startY, startZ)
         .setNormal(normalX, normalY, normalZ).setLineWidth(EntityChamsRenderer.getLineWidth())
         .setColor(EntityChamsRenderer.outlineColor.hashCode());
      EntityChamsRenderer.outlineMesh.addVertex(endX, endY, endZ)
         .setNormal(normalX, normalY, normalZ).setLineWidth(EntityChamsRenderer.getLineWidth())
         .setColor(EntityChamsRenderer.outlineColor.hashCode());
   }
}
