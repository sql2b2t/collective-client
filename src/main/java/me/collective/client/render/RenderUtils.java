package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import me.collective.mixin.GameRendererAccessor;
import java.awt.Color;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL32C;

public final class RenderUtils implements ClientAccess {
   public static final RenderUtils INSTANCE = new RenderUtils();
   public static final Matrix4f WORLD_PROJECTION_MATRIX = new Matrix4f();
   private static final int[] renderColor = new int[4];
   private static float partialTick;
   private static boolean inverseViewBobbing;

   public static void setColorAlpha(int alpha) {
      setColorComponents(
         renderColor[0],
         renderColor[1],
         renderColor[2],
         alpha
      );
   }

   public static BufferSource getEntityBuffers() {
      return ClientAccess.collectiveMinecraft.renderBuffers().bufferSource();
   }

   public static void setLineSmoothing(boolean enabled) {
      if (enabled) {
         GL32C.glEnable(GL32C.GL_LINE_SMOOTH);
      } else {
         GL32C.glDisable(GL32C.GL_LINE_SMOOTH);
      }
   }

   public static void setPartialTick(float framePartialTick) {
      partialTick = framePartialTick;
   }

   private RenderUtils() {
   }

   public static float getPartialTick() {
      return partialTick;
   }

   public static void setLineWidth(float width) {
      GL32C.glLineWidth(width);
   }

   public void renderEntity(
      Entity entity, float partialTick, PoseStack poses, MultiBufferSource buffers
   ) {
      EntityRenderDispatcher dispatcher = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher();
      Vec3 cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer
         .getMainCamera()
         .position();
      double entityX = Mth.lerp(partialTick, entity.xOld, entity.getX());
      double entityY = Mth.lerp(partialTick, entity.yOld, entity.getY());
      double entityZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
      EntityRenderState renderState = dispatcher.extractEntity(entity, partialTick);
      CameraRenderState cameraRenderState = ClientAccess.collectiveMinecraft.gameRenderer.getLevelRenderState().cameraRenderState;
      dispatcher.submit(
         renderState,
         cameraRenderState,
         entityX - cameraPosition.x,
         entityY - cameraPosition.y,
         entityZ - cameraPosition.z,
         poses,
         ClientAccess.collectiveMinecraft.gameRenderer.getSubmitNodeStorage()
      );
   }

   public static void clearColor(RenderTarget target, Color color) {
      int clearColor = ARGB.color(0, color.getRed(), color.getGreen(), color.getBlue());
      RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), clearColor);
   }

   public static void clearColor(RenderTarget target) {
      clearColor(target, Color.white);
   }

   public static void translateToCamera(PoseStack poses, Vec3 position) {
      Vec3 cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer
         .getMainCamera()
         .position();
      double relativeX = position.x - cameraPosition.x;
      double relativeY = position.y - cameraPosition.y;
      double relativeZ = position.z - cameraPosition.z;
      poses.translate(relativeX, relativeY, relativeZ);
   }

   public void drawRectOutline(
      PoseStack poses, float left, float top, float right, float bottom, Color color
   ) {
      if (right < left) {
         float previousLeft = left;
         left = right;
         right = previousLeft;
      }

      if (bottom < top) {
         float previousTop = top;
         top = bottom;
         bottom = previousTop;
      }

      this.drawHorizontalLine(poses, left, right, top, color);
      this.drawVerticalLine(poses, right, top, bottom, color);
      this.drawHorizontalLine(poses, left, right, bottom, color);
      this.drawVerticalLine(poses, left, top, bottom, color);
   }

   protected void drawHorizontalLine(
      PoseStack poses, float left, float right, float y, Color color
   ) {
      if (right < left) {
         float previousLeft = left;
         left = right;
         right = previousLeft;
      }

      this.fillRect(
         poses, left, y, right + 1.0F, y + 1.0F, color
      );
   }

   public static void applyInverseViewBobbing(PoseStack poses, float partialTick) {
      inverseViewBobbing = true;
      if (ClientAccess.collectiveMinecraft.options.bobView().get()) {
         ((GameRendererAccessor)ClientAccess.collectiveMinecraft.gameRenderer)
            .collective$bobView(poses, partialTick);
      }

      inverseViewBobbing = false;
   }

   public static void blitToMain(RenderTarget target, RenderPipeline pipeline) {
      blitToMain(target, pipeline, FilterMode.NEAREST);
   }

   public static void blitToMain(RenderTarget target, RenderPipeline pipeline, FilterMode filter) {
      AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(Mode.QUADS);
      GpuBuffer indexBuffer = indices.getBuffer(6);
      GpuTextureView outputTexture = ClientAccess.collectiveMinecraft.getMainRenderTarget().getColorTextureView();

      try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", outputTexture, OptionalInt.empty())) {
         renderPass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setIndexBuffer(indexBuffer, indices.type());
         renderPass.bindTexture("InSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
         renderPass.drawIndexed(
            0,
            0,
            6,
            1
         );
      }
   }

   public void drawWorldText(
      PoseStack poses, String text, Vec3 position, double scale, Color color, boolean shadow
   ) {
      this.drawWorldText(poses, text, position, 0.0F, 0.0F, scale, color, shadow);
   }

   public void drawWorldTexture(
      PoseStack poses, Vec3 position, float width, float height, double scale, Color color, Identifier texture
   ) {
      poses.pushPose();
      translateToCamera(poses, position);
      poses.mulPose(
         ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.rotation()
      );
      poses.scale(0.025F * (float)scale, -0.025F * (float)scale, 1.0F);
      int halfWidth = (int)(width / 2.0F);
      int halfHeight = (int)(height / 2.0F);
      GuiDrawUtils.drawTexture(
         poses, texture, -halfWidth, -halfHeight, 0.0F, 0.0F, (int)width, (int)height, (int)width, (int)height, color.hashCode()
      );
      getEntityBuffers().endLastBatch();
      poses.popPose();
   }

   public void drawWorldRect(
      PoseStack poses, Vec3 position, float offsetX, float offsetY, float width, float height, double scale, Color color
   ) {
      poses.pushPose();
      translateToCamera(poses, position);
      poses.mulPose(
         ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.rotation()
      );
      poses.translate(offsetX, offsetY, 0.0F);
      poses.scale(0.025F * (float)scale, -0.025F * (float)scale, (float)(0.025F * scale));
      int halfWidth = (int)(width / 2.0F);
      this.fillRect(
         poses, -halfWidth, 0.0F, (int)(width - halfWidth), (int)height, color
      );
      poses.popPose();
   }

   public void fillRect(
      PoseStack poses, float left, float top, float right, float bottom, int z, int color
   ) {
      if (left < right) {
         float previousLeft = left;
         left = right;
         right = previousLeft;
      }

      if (top < bottom) {
         float previousTop = top;
         top = bottom;
         bottom = previousTop;
      }

      BufferBuilder vertices = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      vertices.addVertex(poses.last().pose(), left, bottom, z).setColor(color);
      vertices.addVertex(poses.last().pose(), right, bottom, z).setColor(color);
      vertices.addVertex(poses.last().pose(), right, top, z).setColor(color);
      vertices.addVertex(poses.last().pose(), left, top, z).setColor(color);
      ClientRenderTypes.gui().draw(vertices.buildOrThrow());
   }

   public void fillRect(
      PoseStack poses, float left, float top, float right, float bottom, int color
   ) {
      this.fillRect(poses, left, top, right, bottom, 0, color);
   }

   public void drawWorldText(
      PoseStack poses, String text, Vec3 position, float offsetX, float offsetY, float textX, float textY, double scale, Color color, boolean shadow
   ) {
      poses.pushPose();
      translateToCamera(poses, position);
      poses.mulPose(
         ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera.rotation()
      );
      poses.translate(offsetX, offsetY, 0.0F);
      poses.scale(0.025F * (float)scale, -0.025F * (float)scale, (float)(0.025F * scale));
      if (shadow) {
         FontRenderer.INSTANCE
            .drawWithShadow(poses, text, textX, textY, color, DisplayMode.SEE_THROUGH);
      } else {
         FontRenderer.INSTANCE
            .drawWithoutShadow(poses, text, textX, textY, color.hashCode(), DisplayMode.SEE_THROUGH);
      }

      poses.popPose();
   }

   public void drawWorldText(
      PoseStack poses, String text, Vec3 position, float offsetX, float offsetY, double scale, Color color, boolean shadow
   ) {
      this.drawWorldText(
         poses,
         text,
         position,
         offsetX,
         offsetY,
         -FontRenderer.INSTANCE
               .getWidth(text)
            / 2.0F,
         0.0F,
         scale,
         color,
         shadow
      );
   }

   public void addWorldLine(
      PoseStack poses, BufferBuilder vertices, Vec3 from, Vec3 to, float width, int startColor, int endColor
   ) {
      Vec3 cameraPosition = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher()
         .camera
         .position();
      float fromX = (float)(from.x() - cameraPosition.x());
      float fromY = (float)(from.y() - cameraPosition.y());
      float fromZ = (float)(from.z() - cameraPosition.z());
      float toX = (float)(to.x() - cameraPosition.x());
      float toY = (float)(to.y() - cameraPosition.y());
      float toZ = (float)(to.z() - cameraPosition.z());
      VertexGeometry.appendLine(vertices, poses.last(), fromX, fromY, fromZ, toX, toY, toZ, width, startColor, endColor);
   }

   public void fillRect(
      PoseStack poses, float left, float top, float right, float bottom, Color color
   ) {
      this.fillRect(poses, left, top, right, bottom, color.hashCode());
   }

   public static void setRenderColor(
      float red, float green, float blue, float alpha
   ) {
      renderColor[0] = (int)(red * 255.0F);
      renderColor[1] = (int)(green * 255.0F);
      renderColor[2] = (int)(blue * 255.0F);
      renderColor[3] = (int)(alpha * 255.0F);
   }

   public static void clearDepth(RenderTarget target) {
      RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.getDepthTexture(), 1.0);
   }

   protected void drawVerticalLine(
      PoseStack poses, float x, float top, float bottom, Color color
   ) {
      if (bottom < top) {
         float previousTop = top;
         top = bottom;
         bottom = previousTop;
      }

      this.fillRect(
         poses, x, top + 1.0F, x + 1.0F, bottom, color
      );
   }

   public void flushEntities() {
      ClientAccess.collectiveMinecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
      ClientAccess.collectiveMinecraft.renderBuffers().bufferSource().endBatch();
   }

   public static int[] getRenderColor() {
      return renderColor;
   }

   public static void setColorComponents(
      int red, int green, int blue, int alpha
   ) {
      renderColor[0] = red;
      renderColor[1] = green;
      renderColor[2] = blue;
      renderColor[3] = alpha;
   }

   public static void applyCameraRotation(PoseStack poses) {
      Camera camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera();
      poses.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
      poses.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
   }

   public static void applyCameraRotation(Matrix4f matrix) {
      Camera camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera();
      matrix.rotate(Axis.XP.rotationDegrees(camera.xRot()));
      matrix.rotate(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
   }

   public static void blitToMain(RenderTarget target) {
      blitToMain(
         target, ClientRenderPipelines.BLIT_TRANSLUCENT
      );
   }

   public static void setColorAlpha(float alpha) {
      setColorComponents(
         renderColor[0],
         renderColor[1],
         renderColor[2],
         (int)(alpha * 255.0F)
      );
   }

   public static boolean isInverseViewBobbing() {
      return inverseViewBobbing;
   }

}
