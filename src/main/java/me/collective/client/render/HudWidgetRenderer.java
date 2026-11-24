package me.collective.client.render;

import me.collective.client.gui.CursorType;
import me.collective.client.utils.math.FloatBounds;

import me.collective.client.utils.FloatAnimation;
import me.collective.client.gui.GuiElement;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.gui.screens.ClientScreen;
import me.collective.client.gui.screens.HudEditorScreen;
import me.collective.client.modules.HudWidgetModule;
import me.collective.client.modules.client.FontsModule;
import me.collective.client.settings.options.HudAnchor;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.util.Mth;

public abstract class HudWidgetRenderer implements ClientAccess, GuiElement {

   private static final Color editorBackgroundColor = new Color(
      0,
      0,
      0,120);
   public static boolean renderingHud = false;
   protected final HudWidgetModule widget;
   private final FloatAnimation selectionAnimation = new FloatAnimation();
   private final FloatAnimation enabledAnimation = new FloatAnimation();
   private HudAnchor anchor = HudAnchor.NONE;
   private HudHorizontalAlignment horizontalAlignment = HudHorizontalAlignment.FREE;
   private HudVerticalAlignment verticalAlignment = HudVerticalAlignment.FREE;
   private float relativeX = 0.5F;
   private float relativeY = 0.5F;
   private int horizontalDirection;
   private int verticalDirection;
   private float[] dimensions = new float[2];
   private boolean selected;
   private long hoverStartMillis;

   @Override
   public int getHeight() {
      int height = FontRenderer.INSTANCE
         .getHeight();
      if (FontsModule.INSTANCE.isToggled()) {
         height--;
      }

      return height;
   }

   public boolean isSelected() {
      return this.selected;
   }

   public void setSelected(boolean selected) {
      if (this.selected != selected) {
         this.selectionAnimation
            .setTarget(
               selected ? 1.0F : 0.0F, 250L
            );
         this.selected = selected;
      }
   }

   public float getY() {
      return Math.min(
         this.relativeY
            * ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight(),
         ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight()
            - this.getDimensions()[1]
      );
   }

   public float[] getDimensions() {
      return this.dimensions;
   }

   public float getX() {
      return Math.min(
         this.relativeX
            * ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth(),
         ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth()
            - this.getDimensions()[0]
      );
   }

   public float getEnabledProgress() {
      return this.enabledAnimation
         .getValue();
   }

   protected HudWidgetRenderer(HudWidgetModule widget) {
      this.widget = widget;
   }

   public void updateSnapping(float screenWidth, float screenHeight, float snapDistance) {
      float x = this.getX();
      float y = this.getY();
      float width = this.dimensions[0];
      float height = this.dimensions[1];
      float centerX = x
         + width
            / 2.0F;
      float centerY = y
         + height
            / 2.0F;
      this.resetSnapping();
      if (x < snapDistance) {
         this.setHorizontalAlignment(HudHorizontalAlignment.LEFT);
      } else if (x + width > screenWidth - snapDistance) {
         this.setHorizontalAlignment(HudHorizontalAlignment.RIGHT);
      } else if (Math.abs(
            centerX
               - screenWidth
                  / 2.0F
         )
         <= snapDistance) {
         this.setHorizontalAlignment(HudHorizontalAlignment.CENTER);
      }

      if (y < snapDistance) {
         this.setVerticalAlignment(HudVerticalAlignment.TOP);
      } else if (y + height > screenHeight - snapDistance) {
         this.setVerticalAlignment(HudVerticalAlignment.BOTTOM);
      } else if (Math.abs(
            centerY
               - screenHeight
                  / 2.0F
         )
         <= snapDistance) {
         this.setVerticalAlignment(HudVerticalAlignment.CENTER);
      }
   }

   public float getRelativeY() {
      return this.relativeY;
   }

   public abstract float[] calculateDimensions();

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      float[] size = this.getDimensions();
      if (this.isMouseOver(mouseX, mouseY)
         && !this.selected) {
         if (this.hoverStartMillis == -1L) {
            this.hoverStartMillis = System.currentTimeMillis();
         }

         if (this.hoverStartMillis + 300L <= System.currentTimeMillis()) {
            float labelX = this.getHorizontalDirection() == 1
               ? this.getX()
                  + size[0]
                  + 2.0F
               : this.getX()
                  - 2.0F
                  - FontRenderer.INSTANCE
                     .getWidth(
                        this.widget.getName()
                     );
            HudEditorScreen.hoverLabel
               .show(
                  this.widget.getName(),
                  labelX,
                  this.getY(),
                  this.getHorizontalDirection()
               );
         }
      } else {
         this.hoverStartMillis = -1L;
      }

      if (!renderingHud) {
         if (this.isMouseOver(mouseX, mouseY)) {
            ClientScreen.cursor = CursorType.POINTER;
         }

         float x = this.getX();
         float y = this.getY();
         float editorScale = RenderUtils.getRenderColor()[3]
            / 255.0F;
         float backgroundAlpha = ClientContext.getHudEditor()
               .getTransitionOpacity()
            * editorScale
            * editorBackgroundColor.getAlpha()
            / 255.0F;
         GuiDrawUtils.fillRect(
            poses,
            x,
            y,
            x
               + Math.max(
                  size[0],
                  4.0F
               ),
            y
               + Math.max(
                  size[1],
                  4.0F
               ),
            ColorUtils.toArgb(
               editorBackgroundColor, backgroundAlpha
            )
         );
         float selectionProgress = this.selectionAnimation
            .getValue();
         if (selectionProgress > 0.0F) {
            GuiDrawUtils.drawRectOutline(
               poses,
               x
                  - 1.0F,
               y
                  - 1.0F,
               x
                  + Math.max(
                     size[0],
                     4.0F
                  ),
               y + Math.max(size[1], 4.0F),
               ColorUtils.withAlpha(
                  ColorUtils.getThemeColor(),
                  (int)(
                     selectionProgress
                        * 255.0F
                  )
               )
            );
         }

         if (editorScale
            != 1.0F) {
            GuiDrawUtils.flush();
         }
      }
   }

   public void setAnchor(HudAnchor anchor) {
      this.anchor = anchor;
   }

   public void setHorizontalAlignment(HudHorizontalAlignment alignment) {
      this.horizontalAlignment = alignment;
   }

   public boolean isEnabled() {
      return this.widget.isToggled();
   }

   public void setDimensions(float[] dimensions) {
      this.dimensions = dimensions;
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      float[] size = this.getDimensions();
      return mouseX >= this.getX()
         && mouseX
            <= this.getX()
               + Math.max(
                  size[0],
                  4.0F
               )
         && mouseY >= this.getY()
         && mouseY
            <= this.getY()
               + Math.max(
                  size[1],
                  4.0F
               );
   }

   public void setX(float x, boolean screenCoordinates) {
      if (screenCoordinates) {
         this.relativeX = x
            / ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth();
      } else {
         this.relativeX = Mth.clamp(
            x,
            0.0F,
            1.0F
         );
      }
   }

   public void setVerticalAlignment(HudVerticalAlignment alignment) {
      this.verticalAlignment = alignment;
   }

   public float getContentY(float contentHeight) {
      return this.getVerticalDirection() == -1
         ? this.getY()
            + this.dimensions[1]
            - contentHeight
         : this.getY();
   }

   public float getContentX(float contentWidth) {
      if (this.anchor == HudAnchor.TOP_CENTER) {
         return this.getX()
            + this.dimensions[0]
               / 2.0F
            - contentWidth
               / 2.0F;
      } else {
         return this.getHorizontalDirection() == -1
            ? this.getX()
               + this.dimensions[0]
               - contentWidth
            : this.getX();
      }
   }

   public void setY(float y, boolean screenCoordinates) {
      if (screenCoordinates) {
         this.relativeY = y
            / ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight();
      } else {
         this.relativeY = Mth.clamp(
            y,
            0.0F,
            1.0F
         );
      }
   }

   public FloatBounds getBounds() {
      return FloatBounds.fromPositionAndSize(
         this.relativeX
            * ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth(),
         this.relativeY
            * ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight(),
         this.dimensions[0],
         this.dimensions[1]
      );
   }

   public HudAnchor getAnchor() {
      return this.anchor;
   }

   public HudHorizontalAlignment getHorizontalAlignment() {
      return this.horizontalAlignment;
   }

   public HudVerticalAlignment getVerticalAlignment() {
      return this.verticalAlignment;
   }

   public int getVerticalDirection() {
      if (this.verticalDirection == 0) {
         this.updateDirections();
      }

      return this.verticalDirection;
   }

   public HudWidgetModule getWidgetModule() {
      return this.widget;
   }

   public void resetSnapping() {
      this.horizontalAlignment = HudHorizontalAlignment.FREE;
      this.verticalAlignment = HudVerticalAlignment.FREE;
   }

   public int getHorizontalDirection() {
      if (this.horizontalDirection == 0) {
         this.updateDirections();
      }

      return this.horizontalDirection;
   }

   public void updateDirections() {
      this.horizontalDirection = this.getX()
               + this.dimensions[0]
                  / 2.0F
            > ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth()
               / 2.0F
         ? -1
         : 1;
      this.verticalDirection = this.getY()
               + this.dimensions[1]
                  / 2.0F
            > ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight()
               / 2.0F
         ? -1
         : 1;
   }

   public float getRelativeX() {
      return this.relativeX;
   }

   public void updateEnabledAnimation() {
      this.enabledAnimation
         .setTarget(
            this.isEnabled()
               ? 1.0F
               : 0.0F,
            250L
         );
   }

}
