package me.collective.client.render;

import me.collective.client.ClientAccess;
import me.collective.client.events.WorldRenderEvent;
import me.collective.client.events.EventHandler;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class WorldBoxRenderer implements ClientAccess {
   static final MeshBuffer fillVertices = MeshBuffer.create();
   static final MeshBuffer lineVertices = MeshBuffer.create();

   private WorldBoxRenderer() {
   }

   @EventHandler(priority = -9999)
   private static void onRenderEnd(
      WorldRenderEvent.Main event
   ) {
      flush();
      GuiDrawUtils.flush(true);
   }

   public static void fillBox(
      PoseStack poses, BlockPos position, Color color
   ) {
      fillBox(poses, new AABB(position), color);
   }

   public static void fillBox(
      PoseStack poses, AABB bounds, Color color
   ) {
      fillBox(poses, bounds, color.hashCode());
   }

   public static void fillBox(
      PoseStack poses, AABB bounds, int color
   ) {
      if (ARGB.alpha(color) != 0) {
         Matrix4f poseMatrix = poses.last().pose();
         VertexGeometry.appendFilledBox(
            fillVertices, poseMatrix, bounds, color
         );
      }
   }

   public static void fillFadingBox(
      PoseStack poses, AABB bounds, Color color
   ) {
      if (color.getAlpha() != 0) {
         Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
            .camera
            .position();
         float minX = (float)(bounds.minX - cameraPosition.x());
         float minY = (float)(bounds.minY - cameraPosition.y());
         float minZ = (float)(bounds.minZ - cameraPosition.z());
         float maxX = (float)(bounds.maxX - cameraPosition.x());
         float maxY = (float)(bounds.maxY - cameraPosition.y());
         float maxZ = (float)(bounds.maxZ - cameraPosition.z());
         int bottomColor = color.hashCode();
         int topColor = ColorUtils.toArgb(color, 0);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, maxY, minZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, maxY, minZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, maxY, minZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, maxY, maxZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), maxX, maxY, maxZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, maxY, maxZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, minZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, minY, maxZ
            )
            .setColor(bottomColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, maxY, maxZ
            )
            .setColor(topColor);
         fillVertices.addVertex(
               poses.last().pose(), minX, maxY, minZ
            )
            .setColor(topColor);
      }
   }

   public static void outlineBox(
      PoseStack poses, BlockPos position, Color color, float lineWidth
   ) {
      outlineBox(poses, new AABB(position), color, lineWidth);
   }

   public static void outlineBox(
      PoseStack poses, AABB bounds, Color color, float lineWidth
   ) {
      outlineBox(poses, bounds, color.hashCode(), lineWidth);
   }

   public static void outlineBox(
      PoseStack poses, AABB bounds, int color, float lineWidth
   ) {
      if (!(lineWidth <= 0.0F) && ARGB.alpha(color) > 0) {
         VertexGeometry.appendBoxOutline(lineVertices, poses.last(), bounds, lineWidth, color);
      }
   }

   public static void outlineFadingBox(
      PoseStack poses, AABB bounds, int color, float lineWidth
   ) {
      if (!(lineWidth <= 0.0F) && ARGB.alpha(color) > 0) {
         Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.position();
         float minX = (float)(bounds.minX - cameraPosition.x());
         float minY = (float)(bounds.minY - cameraPosition.y());
         float minZ = (float)(bounds.minZ - cameraPosition.z());
         float maxX = (float)(bounds.maxX - cameraPosition.x());
         float maxY = (float)(bounds.maxY - cameraPosition.y());
         float maxZ = (float)(bounds.maxZ - cameraPosition.z());
         int topColor = ARGB.color(0, color);
         VertexGeometry.appendLine(lineVertices, poses.last(), minX, minY, minZ, minX, maxY, minZ, lineWidth, color, topColor);
         VertexGeometry.appendLine(lineVertices, poses.last(), maxX, minY, minZ, maxX, maxY, minZ, lineWidth, color, topColor);
         VertexGeometry.appendLine(lineVertices, poses.last(), minX, minY, maxZ, minX, maxY, maxZ, lineWidth, color, topColor);
         VertexGeometry.appendLine(lineVertices, poses.last(), maxX, minY, maxZ, maxX, maxY, maxZ, lineWidth, color, topColor);
      }
   }

   public static int applyRenderColor(int color) {
      return modulateColor(
         color, RenderUtils.getRenderColor()
      );
   }

   public static int modulateColor(int color, int[] components) {
      return ColorUtils.modulateArgb(color, components);
   }

   public static void flush() {
      fillVertices.draw(ClientRenderTypes.worldFill());
      lineVertices.draw(ClientRenderTypes.worldLines());
   }

   static {
      ClientAccess.eventBus.subscribeStatic(
         WorldBoxRenderer.class
      );
      fillVertices.setFormat(
         Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
      );
      lineVertices.setFormat(
         Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
      );
   }

}
