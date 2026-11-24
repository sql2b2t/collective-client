package me.collective.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import me.collective.client.ClientAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

public final class GuiProjectionUtils implements ClientAccess {
   public static final CachedOrthoProjectionMatrixBuffer GUI_PROJECTION_CACHE =
      new CachedOrthoProjectionMatrixBuffer("gui_collective", 1000.0F, 11000.0F, true);
   private static GpuBufferSlice savedProjectionBuffer;
   private static ProjectionType savedProjectionType;
   private static Matrix4f savedModelView;

   public static void restoreProjection() {
      if (savedModelView == null) {
         return;
      }
      RenderSystem.setProjectionMatrix(savedProjectionBuffer, savedProjectionType);
      RenderSystem.getModelViewMatrix().set(savedModelView);
      savedProjectionBuffer = null;
      savedProjectionType = null;
      savedModelView = null;
   }

   public static void beginGuiProjection() {
      if (savedModelView != null) {
         return;
      }
      Window window = collectiveMinecraft.getWindow();
      float guiWidth = (float)window.getWidth() / (float)window.getGuiScale();
      float guiHeight = (float)window.getHeight() / (float)window.getGuiScale();
      savedProjectionBuffer = RenderSystem.getProjectionMatrixBuffer();
      savedProjectionType = RenderSystem.getProjectionType();
      Matrix4f modelView = RenderSystem.getModelViewMatrix();
      savedModelView = new Matrix4f(modelView);
      RenderSystem.setProjectionMatrix(
         GUI_PROJECTION_CACHE.getBuffer(guiWidth, guiHeight), ProjectionType.ORTHOGRAPHIC
      );
      modelView.identity().translate(0.0F, 0.0F, -11000.0F);
   }

   public static PoseStack toPoseStack(Matrix3x2fStack guiPose) {
      PoseStack poseStack = new PoseStack();
      Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      matrix.set(
         guiPose.m00(), guiPose.m01(), 0.0F, 0.0F,
         guiPose.m10(), guiPose.m11(), 0.0F, 0.0F,
         0.0F, 0.0F, 1.0F, 0.0F,
         guiPose.m20(), guiPose.m21(), 0.0F, 1.0F
      );
      return poseStack;
   }

   public static PoseStack toPoseStack(GuiGraphics graphics) {
      return toPoseStack(graphics.pose());
   }

   private GuiProjectionUtils() {
   }
}
