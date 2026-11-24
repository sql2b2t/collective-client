package me.collective.client.render;

import me.collective.client.utils.FloatAnimation;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.HudWidgetModule;
import me.collective.client.modules.client.HUDModule;
import me.collective.client.settings.NumberSetting;
import me.collective.client.settings.Setting;
import me.collective.client.utils.math.MathUtils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ResizableHudWidgetRenderer extends HudWidgetRenderer {

   private static HUDModule hudSettings;
   private static HUDModule hudSettings() {
      if (hudSettings == null) {
         hudSettings = (HUDModule)ClientContext.getModule(HUDModule.class);
      }
      return hudSettings;
   }
   static {
      Identifier.fromNamespaceAndPath("collective", "textures/triangle.png");
   }
   private static ResizableHudWidgetRenderer activeResizeRenderer;
   private final Setting<Float> sizeSetting = new NumberSetting<>(
      "Size",
      1.0F,
      0.5F,
      2.0F
   );
   private final FloatAnimation handleAnimation = new FloatAnimation();
   private boolean applyHudScale;
   private boolean resizingEnabled;

   public float getScale() {
      HUDModule hudSettings = hudSettings();
      float scaleMultiplier = this.applyHudScale && hudSettings != null
         ? hudSettings.getTextScale()
         : 1.0F;
      return this.sizeSetting.getValue() * scaleMultiplier;
   }

   private boolean isMouseButtonPressed(int button) {
      return GLFW.glfwGetMouseButton(
            ClientAccess.collectiveMinecraft.getWindow().handle(), button
         )
         == 1;
   }

   public ResizableHudWidgetRenderer(HudWidgetModule widget) {
      super(widget);
   }

   @Override
   public float[] calculateDimensions() {
      float[] dimensions = this.widget
         .calculateDimensions();
      dimensions[0] *= this.getScale();
      dimensions[1] *= this.getScale();
      return dimensions;
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      super.render(poses, mouseX, mouseY);
      float[] sourceDimensions = this.widget
         .calculateDimensions();
      poses.pushPose();
      poses.scale(
         this.getScale(),
         this.getScale(),
         1.0F
      );
      poses.translate(
         this.getX()
            / this.getScale(),
         this.getY()
            / this.getScale(),
         0.0F
      );
      this.widget
         .renderContent(poses);
      if (!renderingHud
         && this.resizingEnabled) {
         boolean handleHovered = this.isResizeHandleHovered(mouseX, mouseY);
         if (activeResizeRenderer == this) {
            double contentWidth = sourceDimensions[0];
            double contentHeight = sourceDimensions[1];
            double resizeScale = Math.min(
               (mouseX - this.getX()) / contentWidth,
               (mouseY - this.getY()) / contentHeight
            );
            this.sizeSetting
               .setValue(
                  (float)MathUtils.floorToDecimals(
                     resizeScale,2)
               );
         }

         if (handleHovered && this.isMouseButtonPressed(1)) {
            this.sizeSetting
               .setValue(
                  1.0F
               );
         }

         if (handleHovered && this.isMouseButtonPressed(0)) {
            activeResizeRenderer = this;
         } else if (!this.isMouseButtonPressed(0)) {
            activeResizeRenderer = null;
         }

         if (activeResizeRenderer == this) {
            this.handleAnimation
               .setTarget(
                  0.6F,
                  250L
               );
         } else if (handleHovered) {
            this.handleAnimation
               .setTarget(
                  0.4F,
                  250L
               );
         } else {
            this.handleAnimation
               .setTarget(
                  0.2F,
                  250L
               );
         }

         int handleColor = ColorUtils.toArgb(
            ColorUtils.getThemeColor(),
            this.handleAnimation
                  .getValue()
               * ClientContext.getHudEditor()
                  .getTransitionOpacity()
         );
         VertexConsumer handleVertices = RenderUtils.getEntityBuffers()
            .getBuffer(ClientRenderTypes.guiTriangles());
         handleVertices.addVertex(
               poses.last(),
               sourceDimensions[0]
                  - 5.0F,
               sourceDimensions[1] + 0.0F,
               0.0F
            )
            .setColor(handleColor);
         handleVertices.addVertex(poses.last(), sourceDimensions[0] + 0.0F, sourceDimensions[1] + 0.0F, 0.0F).setColor(handleColor);
         handleVertices.addVertex(
               poses.last(),
               sourceDimensions[0] + 0.0F,
               sourceDimensions[1]
                  - 5.0F,
               0.0F
            )
            .setColor(handleColor);
      }

      poses.popPose();
   }

   public boolean isResizeHandleHovered(double mouseX, double mouseY) {
      if (!this.resizingEnabled) {
         return false;
      }

      float[] dimensions = this.getDimensions();
      float right = this.getX()
         + Math.max(
            dimensions[0],
            4.0F
         );
      float bottom = this.getY()
         + Math.max(
            dimensions[1],
            4.0F
         );
      return mouseX
            >= right
               - 5.0F
         && mouseX <= right
         && mouseY
            >= bottom
               - 5.0F
         && mouseY <= bottom;
   }

   public void enableResizing(HudWidgetModule widget) {
      this.resizingEnabled = true;
      widget.register(this.sizeSetting);
   }

   public void enableHudScale() {
      this.applyHudScale = true;
   }

}
