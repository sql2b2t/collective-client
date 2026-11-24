package me.collective.client.render;

import me.collective.client.ClientAccess;
import me.collective.client.events.WorldRenderEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class LineMeshRenderer implements ClientAccess {
   private static final Matrix4f projectionMatrix = new Matrix4f();
   private static final Matrix4f inverseProjectionMatrix = new Matrix4f();
   private static final Vector3f positionVector = new Vector3f();
   private static final Vector2f projectedPosition = new Vector2f();
   private static final Vector2f tracerPosition = new Vector2f();
   private static final Vector4f clipPosition = new Vector4f();
   private static final Vector3f pendingPosition = new Vector3f();
   static final MeshBuffer vertices = MeshBuffer.create();
   static PoseStack poseStack;
   private static int pendingColor;
   private static boolean pendingVertex;
   private static float lineWidth;

   public static void updateProjection(PoseStack poses) {
      projectionMatrix.set(
            RenderUtils.WORLD_PROJECTION_MATRIX
         )
         .mul(poses.last().pose());
      inverseProjectionMatrix.set(
            projectionMatrix
         )
         .invert();
   }

   public static void begin(WorldRenderEvent event, float width) {
      begin(event.getPoseStack(), width);
   }

   public static void begin(PoseStack poses, float width) {
      vertices.begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
      poseStack = poses;
      pendingVertex = false;
      lineWidth = width;
      updateProjection(poses);
   }

   public static void addVertex(
      double x, double y, double z, Color color
   ) {
      addVertex(x, y, z, color.hashCode());
   }

   public static void addVertex(
      double x, double y, double z, int color
   ) {
      addVertex(x, y, z, color, false, false);
   }

   public static void addVertex(
      double x, double y, double z, int color, boolean relativeToCamera, boolean clamp
   ) {
      if (relativeToCamera) {
         x = (float)(
            x
               - ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
                  .camera
                  .position()
                  .x()
         );
         y = (float)(
            y
               - ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
                  .camera
                  .position()
                  .y()
         );
         z = (float)(
            z
               - ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
                  .camera
                  .position()
                  .z()
         );
      }

      if (clamp) {
         Vector3f clampedPosition = clampToViewport(
            (float)x, (float)y, (float)z
         );
         x = clampedPosition.x;
         y = clampedPosition.y;
         z = clampedPosition.z;
      }

      if (pendingVertex) {
         VertexGeometry.appendLine(
            vertices, poseStack.last(), pendingPosition.x(), pendingPosition.y(), pendingPosition.z(),
            (float)x, (float)y, (float)z, lineWidth, pendingColor, color
         );
      } else {
         pendingPosition.set(x, y, z);
         pendingColor = color;
      }
      pendingVertex = !pendingVertex;
   }

   public static void addTracer(Vec3 position, Color color) {
      addTracer(position, color.hashCode());
   }

   public static void addTracer(Vec3 position, int color) {
      Vector2f projected = projectTracer(position);
      if (projected == null) {
         return;
      }

      Vector3f origin = unproject(0.0F, 0.0F);
      Vector3f target = unproject(projected.x, projected.y);
      addVertex(
         origin.x, origin.y, origin.z, color, false, false
      );
      addVertex(
         target.x, target.y, target.z, color, false, false
      );
   }

   public static void addLine(
      Vec3 from, Vec3 to, Color color
   ) {
      addLine(from, to, color.hashCode());
   }

   public static void addLine(
      Vec3 from, Vec3 to, int color
   ) {
      addVertex(
         from.x, from.y, from.z, color, true, false
      );
      addVertex(
         to.x, to.y, to.z, color, true, true
      );
   }

   public static void draw() {
      vertices.draw(ClientRenderTypes.worldLines());
   }

   private static Vector3f clampToViewport(
      float x, float y, float z
   ) {
      positionVector.set(x, y, z);
      projectionMatrix.transform(
         clipPosition.set(
            positionVector, 1.0F
         )
      );
      if (Math.abs(clipPosition.w) < 1.0E-6F) {
         return positionVector;
      }

      float inverseW = 1.0F / clipPosition.w;
      float projectedX = clipPosition.x * inverseW;
      float projectedY = clipPosition.y * inverseW;
      boolean outsideViewport = clipPosition.w <= 0.0F
         || projectedX < -1.0F
         || projectedX > 1.0F
         || projectedY < -1.0F
         || projectedY > 1.0F;
      if (!outsideViewport) {
         return positionVector;
      }

      projectedPosition.set(projectedX, projectedY);
      float edgeScale = Math.min(
         1.0F / Math.abs(projectedPosition.x),
         1.0F / Math.abs(projectedPosition.y)
      );
      projectedPosition.mul(edgeScale);
      clipPosition.set(
         projectedPosition.x,
         projectedPosition.y,
         clipPosition.z * inverseW,
         1.0F
      );
      clipPosition.mul(
         inverseProjectionMatrix
      );
      if (clipPosition.w == 0.0F) {
         return positionVector;
      }

      Vector3f clampedPosition = new Vector3f(
         clipPosition.x,
         clipPosition.y,
         clipPosition.z
      ).div(clipPosition.w);
      return Float.isFinite(clampedPosition.x)
         && Float.isFinite(clampedPosition.y)
         && Float.isFinite(clampedPosition.z)
         ? clampedPosition
         : positionVector;
   }

   private static Vector3f unproject(float x, float y) {
      clipPosition.set(x, y, 0.0F, 1.0F);
      inverseProjectionMatrix.transform(clipPosition);
      return new Vector3f(clipPosition.x, clipPosition.y, clipPosition.z).div(clipPosition.w);
   }

   private static Vector2f projectTracer(Vec3 position) {
      Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.position();
      projectionMatrix.transform(
         clipPosition.set(
            (float)(position.x - cameraPosition.x),
            (float)(position.y - cameraPosition.y),
            (float)(position.z - cameraPosition.z),
            1.0F
         )
      );
      float w = clipPosition.w;
      boolean behind = w <= 1.0E-4F;
      float x = behind ? clipPosition.x : clipPosition.x / w;
      float y = behind ? clipPosition.y : clipPosition.y / w;
      float extent = Math.max(Math.abs(x), Math.abs(y));
      if (behind && extent < 1.0E-6F) {
         x = 0.0F;
         y = -1.0F;
         extent = 1.0F;
      }

      if (behind || extent > 1.0F) {
         x /= extent;
         y /= extent;
      } else if (extent < 1.0E-5F) {
         return null;
      }

      return tracerPosition.set(x, y);
   }

}
