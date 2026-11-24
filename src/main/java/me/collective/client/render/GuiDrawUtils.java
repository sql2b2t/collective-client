package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class GuiDrawUtils implements ClientAccess {
   static final MeshBuffer quadBuffer = MeshBuffer.create();
   static final MeshBuffer lineBuffer = MeshBuffer.create();
   static float lineWidth = 1.0F;

   public static void flush() {
      flush(true);
   }

   public static VertexConsumer getLineVertices() {
      return lineBuffer;
   }

   private GuiDrawUtils() {
   }

   static {
      quadBuffer.setFormat(
         Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
      );
      lineBuffer.setFormat(
         Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
      );
   }

   private static void fillVerticalStrip(
      PoseStack poseStack, float x, float top, float bottom, float thickness, Color color
   ) {
      fillRect(poseStack, x, top + thickness, x + thickness, bottom, color);
   }

   private static void drawTexturedQuad(
      PoseStack poseStack, Identifier texture, int left, int right, int top, int bottom, float minU, float maxU, float minV, float maxV, int color
   ) {
      RenderType renderType = ClientRenderTypes.image(texture, false);
      Matrix4f matrix = poseStack.last().pose();
      VertexConsumer vertices = ClientAccess.collectiveMinecraft.renderBuffers()
         .bufferSource()
         .getBuffer(renderType);
      vertices.addVertex(matrix, left, top, 0.0F).setUv(minU, minV).setColor(color);
      vertices.addVertex(matrix, left, bottom, 0.0F).setUv(minU, maxV).setColor(color);
      vertices.addVertex(matrix, right, bottom, 0.0F).setUv(maxU, maxV).setColor(color);
      vertices.addVertex(matrix, right, top, 0.0F).setUv(maxU, minV).setColor(color);
   }

   public static void drawItem(
      PoseStack poseStack, ItemStack stack, float x, float y, float z
   ) {
      drawItem(poseStack, stack, x, y, z, true);
   }

   public static void drawTexture(
      PoseStack poseStack, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color
   ) {
      drawTexturedQuad(
         poseStack, texture, x, x + width, y, y + height, (u + 0.0F) / textureWidth, (u + regionWidth) / textureWidth, (v + 0.0F) / textureHeight, (v + regionHeight) / textureHeight, color
      );
   }

   public static void drawTexture(
      PoseStack poseStack, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color
   ) {
      drawTexture(
         poseStack, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color
      );
   }

   public static void drawItem(
      PoseStack poseStack, ItemStack stack, float x, float y, float z, boolean flushItemBuffers
   ) {
      if (!stack.isEmpty()) {
         ItemStackRenderState renderState = new ItemStackRenderState();
         SubmitNodeStorage submitNodes = ClientAccess.collectiveMinecraft.gameRenderer.getSubmitNodeStorage();
         ClientAccess.collectiveMinecraft.getItemModelResolver()
            .updateForTopItem(
               renderState,
               stack,
               ItemDisplayContext.GUI,
               ClientAccess.collectiveMinecraft.level,
               null,
               0
            );
         int depthScale = 1;
         if (renderState.usesBlockLight()) {
            ClientAccess.collectiveMinecraft.gameRenderer
               .getLighting()
               .setupFor(Entry.ITEMS_3D);
            depthScale = 16;
         } else {
            ClientAccess.collectiveMinecraft.gameRenderer
               .getLighting()
               .setupFor(Entry.ITEMS_FLAT);
         }

         poseStack.pushPose();
         poseStack.translate(x + 8.0F, y + 8.0F, 150.0F + z);

         try {
            poseStack.scale(16.0F, -16.0F, depthScale);
            renderState.submit(
               poseStack,
               submitNodes,
               15728880,
               OverlayTexture.NO_OVERLAY,
               0
            );
            if (flushItemBuffers) {
               RenderUtils.INSTANCE
                  .flushEntities();
            }
         } catch (Throwable failure) {
            throw new ReportedException(CrashReport.forThrowable(failure, "Rendering item"));
         }

         poseStack.popPose();
      }
   }

   public static void drawDurabilityBar(
      PoseStack poseStack, ItemStack stack, float x, float y
   ) {
      if (stack.isBarVisible()) {
         float barX = x + 2.0F;
         float barY = y + 13.0F;
         poseStack.translate(0.0F, 0.0F, 1.0F);
         fillRect(
            poseStack, barX, barY, barX + 13.0F, barY + 2.0F, Color.black
         );
         fillRect(
            poseStack, barX, barY, barX + stack.getBarWidth(), barY + 1.0F, ARGB.opaque(stack.getBarColor())
         );
         poseStack.translate(0.0F, 0.0F, -1.0F);
      }
   }

   public static void fillGradientRect(
      Matrix4f matrix, float x, float y, float width, float height, int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor
   ) {
      quadBuffer.addVertex(matrix, x, y + height, 0.0F)
         .setColor(bottomLeftColor);
      quadBuffer.addVertex(matrix, x + width, y + height, 0.0F)
         .setColor(bottomRightColor);
      quadBuffer.addVertex(matrix, x + width, y, 0.0F)
         .setColor(topRightColor);
      quadBuffer.addVertex(matrix, x, y, 0.0F)
         .setColor(topLeftColor);
   }

   public static void fillHorizontalGradient(
      Matrix4f matrix, float x, float y, float width, float height, int... colors
   ) {
      if (colors.length >= 2) {
         float segmentWidth = width / (colors.length - 1);

         for (int index = 1; index < colors.length; index++) {
            float segmentEnd = index == colors.length - 1 ? width : segmentWidth * index;
            int rightColor = colors[index];
            int leftColor = colors[index - 1];
            quadBuffer.addVertex(matrix, x + segmentEnd, y, 0.0F)
               .setColor(rightColor);
            quadBuffer.addVertex(
                  matrix, x + segmentWidth * (index - 1), y, 0.0F
               )
               .setColor(leftColor);
            quadBuffer.addVertex(
                  matrix, x + segmentWidth * (index - 1), y + height, 0.0F
               )
               .setColor(leftColor);
            quadBuffer.addVertex(
                  matrix, x + segmentEnd, y + height, 0.0F
               )
               .setColor(rightColor);
         }
      }
   }

   public static void drawText(
      PoseStack poseStack, String text, int x, int y, int color, boolean shadow
   ) {
      if (shadow) {

         FontRenderer initializedFontRenderer = FontRenderer.INSTANCE;
      }
      ClientAccess.collectiveMinecraft.font
         .drawInBatch(
            text,
            x,
            y,
            color,
            shadow,
            poseStack.last().pose(),
            FontRenderer.INSTANCE
               .getRenderer()
               .getBufferSource(),
            DisplayMode.NORMAL,
            0,
            15728880
         );
   }

   public static void drawLineRect(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      int packedColor = color.hashCode();
      if (color.getAlpha() != 0) {
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, bottom, 0.0F, right, bottom, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, bottom, 0.0F, right, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), right, top, 0.0F, left, top, 0.0F, lineWidth, packedColor);
         VertexGeometry.appendLine(lineBuffer, poseStack.last(), left, top, 0.0F, left, bottom, 0.0F, lineWidth, packedColor);
      }
   }

   /** Прямой отрезок в line-буфер; width — толщина штриха в пикселях GUI. */
   public static void drawLine(
      PoseStack poseStack, float x1, float y1, float x2, float y2, float width, int color
   ) {
      VertexGeometry.appendLine(lineBuffer, poseStack.last(), x1, y1, 0.0F, x2, y2, 0.0F, width, color);
   }

   /** Рамка скруглённого прямоугольника: 4 прямых отрезка + 4 дуги (по segments сегментов на четверть), штрих 1px. */
   public static void drawRoundedBorder(
      PoseStack poseStack, float left, float top, float right, float bottom, float radius, int color, int segments
   ) {
      if (radius < 1.0F) {
         drawLine(poseStack, left, top, right, top, 1.0F, color);
         drawLine(poseStack, right, top, right, bottom, 1.0F, color);
         drawLine(poseStack, right, bottom, left, bottom, 1.0F, color);
         drawLine(poseStack, left, bottom, left, top, 1.0F, color);
         return;
      }
      float clampedRadius = Math.min(radius, Math.min(right - left, bottom - top) / 2.0F);
      drawLine(poseStack, left + clampedRadius, top, right - clampedRadius, top, 1.0F, color);
      drawLine(poseStack, right, top + clampedRadius, right, bottom - clampedRadius, 1.0F, color);
      drawLine(poseStack, right - clampedRadius, bottom, left + clampedRadius, bottom, 1.0F, color);
      drawLine(poseStack, left, bottom - clampedRadius, left, top + clampedRadius, 1.0F, color);
      float halfPi = (float) (Math.PI / 2.0);
      appendBorderArc(poseStack, left + clampedRadius, top + clampedRadius, clampedRadius, (float) Math.PI, segments, color);
      appendBorderArc(poseStack, right - clampedRadius, top + clampedRadius, clampedRadius, -halfPi, segments, color);
      appendBorderArc(poseStack, right - clampedRadius, bottom - clampedRadius, clampedRadius, 0.0F, segments, color);
      appendBorderArc(poseStack, left + clampedRadius, bottom - clampedRadius, clampedRadius, halfPi, segments, color);
   }

   /** Четверть дуги рамки от угла startAngle против часовой стрелки (cos/sin по четвертям). */
   private static void appendBorderArc(
      PoseStack poseStack, float centerX, float centerY, float radius, float startAngle, int segments, int color
   ) {
      float step = (float) (Math.PI / 2.0) / segments;
      float previousX = centerX + radius * Mth.cos(startAngle);
      float previousY = centerY + radius * Mth.sin(startAngle);
      for (int index = 1; index <= segments; index++) {
         float angle = startAngle + step * index;
         float currentX = centerX + radius * Mth.cos(angle);
         float currentY = centerY + radius * Mth.sin(angle);
         drawLine(poseStack, previousX, previousY, currentX, currentY, 1.0F, color);
         previousX = currentX;
         previousY = currentY;
      }
   }

   public static void drawRectOutline(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      fillHorizontalStrip(poseStack, left, right, top, 1.0F, color);
      fillVerticalStrip(poseStack, right, top, bottom, 1.0F, color);
      fillHorizontalStrip(poseStack, left, right, bottom, 1.0F, color);
      fillVerticalStrip(poseStack, left, top, bottom, 1.0F, color);
   }

   public static void drawItemCooldown(
      PoseStack poseStack, ItemStack stack, float x, float y
   ) {
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;
      float partialTick = ClientAccess.collectiveMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      float cooldown = player == null ? 0.0F : player.getCooldowns().getCooldownPercent(stack, partialTick);
      if (cooldown > 0.0F) {
         float top = y + Mth.floor(16.0F * (1.0F - cooldown));
         float bottom = top + Mth.ceil(16.0F * cooldown);
         fillRect(
            poseStack,
            x,
            top,
            x + 16.0F,
            bottom,
            0x7FFFFFFF
         );
      }
   }

   public static void fillRect(
      PoseStack poseStack, float left, float top, float right, float bottom, int color
   ) {
      if (ARGB.alpha(color) != 0) {
         quadBuffer.addVertex(
               poseStack.last().pose(), left, bottom, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), right, bottom, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), right, top, 0.0F
            )
            .setColor(color);
         quadBuffer.addVertex(
               poseStack.last().pose(), left, top, 0.0F
            )
            .setColor(color);
      }
   }

   private static void fillQuad(
      Matrix4f matrix, float x1, float y1, float x2, float y2, int color
   ) {
      quadBuffer.addVertex(matrix, x1, y2, 0.0F).setColor(color);
      quadBuffer.addVertex(matrix, x2, y2, 0.0F).setColor(color);
      quadBuffer.addVertex(matrix, x2, y1, 0.0F).setColor(color);
      quadBuffer.addVertex(matrix, x1, y1, 0.0F).setColor(color);
   }

   private static void fillCornerFan(
      Matrix4f matrix, float centerX, float centerY, float radius, float startAngle, float sweep, int segments, int color
   ) {
      float previousX = centerX + radius * Mth.cos(startAngle);
      float previousY = centerY + radius * Mth.sin(startAngle);
      float step = sweep / segments;
      for (int index = 1; index <= segments; index++) {
         float angle = startAngle + step * index;
         float currentX = centerX + radius * Mth.cos(angle);
         float currentY = centerY + radius * Mth.sin(angle);
         quadBuffer.addVertex(matrix, centerX, centerY, 0.0F).setColor(color);
         quadBuffer.addVertex(matrix, previousX, previousY, 0.0F).setColor(color);
         quadBuffer.addVertex(matrix, currentX, currentY, 0.0F).setColor(color);
         quadBuffer.addVertex(matrix, currentX, currentY, 0.0F).setColor(color);
         previousX = currentX;
         previousY = currentY;
      }
   }

   /** Скруглённый прямоугольник: центр + 4 кромки + 4 угловых веера. Радиус клампится в половину меньшей стороны. */
   public static void fillRoundedRect(
      PoseStack poseStack, float left, float top, float right, float bottom, float radius, int color
   ) {
      if (ARGB.alpha(color) == 0) {
         return;
      }
      float width = right - left;
      float height = bottom - top;
      if (width <= 0.0F || height <= 0.0F) {
         return;
      }
      float clampedRadius = Math.min(radius, Math.min(width, height) / 2.0F);
      if (clampedRadius <= 0.5F) {
         fillRect(poseStack, left, top, right, bottom, color);
         return;
      }
      Matrix4f matrix = poseStack.last().pose();
      fillQuad(matrix, left + clampedRadius, top, right - clampedRadius, bottom, color);
      fillQuad(matrix, left, top + clampedRadius, left + clampedRadius, bottom - clampedRadius, color);
      fillQuad(matrix, right - clampedRadius, top + clampedRadius, right, bottom - clampedRadius, color);
      fillQuad(matrix, left + clampedRadius, top, right - clampedRadius, top + clampedRadius, color);
      fillQuad(matrix, left + clampedRadius, bottom - clampedRadius, right - clampedRadius, bottom, color);
      int segments = Math.max(3, Mth.ceil(clampedRadius * 0.9F));
      float halfPi = (float) (Math.PI / 2.0);
      fillCornerFan(matrix, left + clampedRadius, top + clampedRadius, clampedRadius, (float) Math.PI, halfPi, segments, color);
      fillCornerFan(matrix, right - clampedRadius, top + clampedRadius, clampedRadius, (float) -halfPi, halfPi, segments, color);
      fillCornerFan(matrix, right - clampedRadius, bottom - clampedRadius, clampedRadius, 0.0F, halfPi, segments, color);
      fillCornerFan(matrix, left + clampedRadius, bottom - clampedRadius, clampedRadius, halfPi, halfPi, segments, color);
   }

   /** Заполненный круг (квад-веер вокруг центра). */
   public static void fillCircle(
      PoseStack poseStack, float centerX, float centerY, float radius, int color
   ) {
      fillRoundedRect(poseStack, centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, color);
   }

   public static void fillVerticalGradient(
      PoseStack poseStack, float x, float y, float width, float height, int topColor, int bottomColor
   ) {
      fillGradientRect(
         poseStack.last().pose(), x, y, width, height, topColor, topColor, bottomColor, bottomColor
      );
   }

   public static void fillRect(
      PoseStack poseStack, float left, float top, float right, float bottom, Color color
   ) {
      fillRect(poseStack, left, top, right, bottom, color.hashCode());
   }

   private static void fillHorizontalStrip(
      PoseStack poseStack, float left, float right, float y, float thickness, Color color
   ) {
      fillRect(poseStack, left, y, right + thickness, y + thickness, color);
   }

   public static void fillHorizontalGradient(
      PoseStack poseStack, float x, float y, float width, float height, int leftColor, int rightColor
   ) {
      fillGradientRect(
         poseStack.last().pose(), x, y, width, height, leftColor, rightColor, rightColor, leftColor
      );
   }

   public static void flush(boolean flushText) {
      MeshDrawContext.setRenderType(
         ClientRenderTypes.gui()
      );
      quadBuffer.draw();
      MeshDrawContext.setRenderType(
         ClientRenderTypes.worldLines()
      );
      lineBuffer.draw();
      if (flushText) {
         FontRenderer.INSTANCE
            .flush();
      }
   }

}
