package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.util.Mth;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class VertexGeometry implements ClientAccess {
   private static final Vector3f segmentStart = new Vector3f();
   private static final Vector3f segmentEnd = new Vector3f();

   public static void appendFilledBox(
      VertexConsumer vertices, Matrix4f poseMatrix, AABB bounds, int color
   ) {
      if (ARGB.alpha(color) != 0) {
         Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
            .camera
            .position();
         float minX = (float)(bounds.minX - cameraPosition.x());
         float minY = (float)(bounds.minY - cameraPosition.y());
         float minZ = (float)(bounds.minZ - cameraPosition.z());
         float maxX = (float)(bounds.maxX - cameraPosition.x());
         float maxY = (float)(bounds.maxY - cameraPosition.y());
         float maxZ = (float)(bounds.maxZ - cameraPosition.z());
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color);
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color);
      }
   }

   public static void appendBoxOutline(
      VertexConsumer vertices, Pose pose, AABB bounds, float width, int color
   ) {
      Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.position();
      float minX = (float)(bounds.minX - cameraPosition.x());
      float minY = (float)(bounds.minY - cameraPosition.y());
      float minZ = (float)(bounds.minZ - cameraPosition.z());
      float maxX = (float)(bounds.maxX - cameraPosition.x());
      float maxY = (float)(bounds.maxY - cameraPosition.y());
      float maxZ = (float)(bounds.maxZ - cameraPosition.z());
      appendLine(vertices, pose, minX, minY, minZ, maxX, minY, minZ, width, color);
      appendLine(vertices, pose, minX, minY, minZ, minX, minY, maxZ, width, color);
      appendLine(vertices, pose, minX, minY, maxZ, maxX, minY, maxZ, width, color);
      appendLine(vertices, pose, maxX, minY, maxZ, maxX, minY, minZ, width, color);
      if (Math.abs(maxY - minY) < 0.001) {
         return;
      }
      appendLine(vertices, pose, minX, minY, minZ, minX, maxY, minZ, width, color);
      appendLine(vertices, pose, maxX, minY, minZ, maxX, maxY, minZ, width, color);
      appendLine(vertices, pose, minX, minY, maxZ, minX, maxY, maxZ, width, color);
      appendLine(vertices, pose, maxX, minY, maxZ, maxX, maxY, maxZ, width, color);
      appendLine(vertices, pose, minX, maxY, minZ, maxX, maxY, minZ, width, color);
      appendLine(vertices, pose, minX, maxY, minZ, minX, maxY, maxZ, width, color);
      appendLine(vertices, pose, minX, maxY, maxZ, maxX, maxY, maxZ, width, color);
      appendLine(vertices, pose, maxX, maxY, maxZ, maxX, maxY, minZ, width, color);
   }

   public static void appendLine(
      VertexConsumer vertices, Pose pose, float fromX, float fromY, float fromZ,
      float toX, float toY, float toZ, float width, int color
   ) {
      appendLine(vertices, pose, fromX, fromY, fromZ, toX, toY, toZ, width, color, color);
   }

   public static void appendLine(
      VertexConsumer vertices, Pose pose, float fromX, float fromY, float fromZ,
      float toX, float toY, float toZ, float width, int startColor, int endColor
   ) {
      float deltaX = toX - fromX;
      float deltaY = toY - fromY;
      float deltaZ = toZ - fromZ;
      float length = Mth.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
      if (length < 1.0E-6F) {
         return;
      }
      float normalX = deltaX / length;
      float normalY = deltaY / length;
      float normalZ = deltaZ / length;
      vertices.addVertex(pose, fromX, fromY, fromZ).setColor(startColor)
         .setNormal(pose, normalX, normalY, normalZ).setLineWidth(width);
      vertices.addVertex(pose, toX, toY, toZ).setColor(endColor)
         .setNormal(pose, normalX, normalY, normalZ).setLineWidth(width);
   }

   public static void forEachCircleSegment(
      float radius, int segments, VertexGeometry.CircleSegmentConsumer consumer
   ) {
      if (segments < 2) {
         throw new IllegalArgumentException("You need at least 2 segments to iterate circle");
      }

      double previousX = radius;
      double previousZ = 0.0;

      for (int segment = 1; segment <= segments; segment++) {
         double angle = (Math.PI * 2) * segment / segments;
         double x = radius * Math.cos(angle);
         double z = radius * Math.sin(angle);
         segmentStart.set(previousX, 0.0, previousZ);
         segmentEnd.set(x, 0.0, z);
         consumer.accept(
            segmentStart,
            segmentEnd
         );
         previousX = x;
         previousZ = z;
      }
   }

   public interface CircleSegmentConsumer {
      void accept(Vector3f start, Vector3f end);
   }
}
