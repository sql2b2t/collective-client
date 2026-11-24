package me.collective.client.render;

import me.collective.client.gui.GuiElement;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.client.HUDModule;
import me.collective.client.settings.options.HudAnchor;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class HudAnchorGroup implements ClientAccess, GuiElement {

   private static HUDModule hud;
   private static HUDModule hud() {
      if (hud == null) {
         hud = (HUDModule)ClientContext.getModule(HUDModule.class);
      }
      return hud;
   }
   private final List<HudWidgetRenderer> widgets = new ArrayList<>();
   private final HudAnchor anchor;

   public List<HudWidgetRenderer> getWidgets() {
      return this.widgets;
   }

   public HudAnchorGroup(HudAnchor anchor) {
      this.anchor = anchor;
   }

   private void prepareWidgetRender(float enabledProgress) {
      if (!HudWidgetRenderer.renderingHud
         && enabledProgress
            != 1.0F) {
         GuiDrawUtils.flush();
      }
   }

   private float getBottomMargin(boolean rightAligned) {
      HUDModule hud = hud();
      if (hud == null) {
         return 0.0F;
      }
      float sideOffset = rightAligned
         ? hud.getBottomHudOffset()
         : hud.getChatInputOffset();
      return Mth.clamp(
         hud.getSafeY()
            + sideOffset,
         0.0F,
         16.0F
      );
   }

   private float calculateY(
      float height, HudHorizontalAlignment horizontalAlignment, HudVerticalAlignment verticalAlignment
   ) {
      HUDModule hud = hud();
      if (hud == null) {
         return 0.0F;
      }
      return switch (verticalAlignment) {
         case TOP -> hud.getSafeY()
            + (horizontalAlignment == HudHorizontalAlignment.RIGHT ? hud.getEffectIconOffset() : 0.0F);
         case BOTTOM -> ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight()
            - height - this.getBottomMargin(horizontalAlignment == HudHorizontalAlignment.RIGHT);
         case CENTER -> (ClientAccess.collectiveMinecraft.getWindow().getGuiScaledHeight() - height) / 2.0F;
         default -> throw new IllegalStateException("Unexpected value: " + verticalAlignment);
      };
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      poseStack.pushPose();
      if (this.anchor != HudAnchor.NONE) {
         this.applyHudTranslation(poseStack);
      }

      for (int widgetIndex = 0; widgetIndex < this.widgets.size(); widgetIndex++) {
         HudWidgetRenderer widget = (HudWidgetRenderer)this.widgets
            .get(widgetIndex);
         widget.updateEnabledAnimation();
         float enabledProgress = widget.getEnabledProgress();
         boolean visible = widget.isEnabled() || enabledProgress > 0.0F;
         float[] size;
         if (!visible) {
            size = new float[2];
         } else {
            size = widget.calculateDimensions();
         }

         widget.setDimensions(size);
         if (this.anchor != HudAnchor.NONE) {
            this.layoutAnchoredWidget(widget, size, widgetIndex);
         } else {
            this.layoutFreeWidget(widget, size);
         }

         this.prepareWidgetRender(enabledProgress);
         if (visible) {
            RenderUtils.setRenderColor(
               1.0F,
               1.0F,
               1.0F,
               enabledProgress
            );
            widget.render(poseStack, mouseX, mouseY);
            this.prepareWidgetRender(enabledProgress);
            if (!HudWidgetRenderer.renderingHud) {
               RenderUtils.getEntityBuffers()
                  .endBatch(ClientRenderTypes.guiTriangles());
            }

            RenderUtils.setRenderColor(
               1.0F,
               1.0F,
               1.0F,
               1.0F
            );
         }

         if (widgetIndex < this.widgets.size() - 1
            && this.anchor != HudAnchor.NONE
            && this.anchor.getVerticalAlignment() != HudVerticalAlignment.BOTTOM) {
            ((HudWidgetRenderer)this.widgets.get(widgetIndex + 1))
               .setY(
                  widget.getY() + size[1], true
               );
         }
      }

      poseStack.popPose();
   }

   private void layoutFreeWidget(HudWidgetRenderer widget, float[] size) {
      HudHorizontalAlignment horizontalAlignment = widget.getHorizontalAlignment();
      HudVerticalAlignment verticalAlignment = widget.getVerticalAlignment();
      if (horizontalAlignment != HudHorizontalAlignment.FREE) {
         widget.setX(
            this.calculateX(size[0], horizontalAlignment), true
         );
      }

      if (verticalAlignment != HudVerticalAlignment.FREE) {
         widget.setY(
            this.calculateY(size[1], horizontalAlignment, verticalAlignment), true
         );
      }
   }

   private void layoutAnchoredWidget(
      HudWidgetRenderer widget, float[] size, int widgetIndex
   ) {
      widget.setX(
         this.calculateX(
            size[0],
            this.anchor
               .getHorizontalAlignment()
         ),
         true
      );
      if (widgetIndex == 0) {
         widget.setY(
            this.calculateY(
               size[1],
               this.anchor
                  .getHorizontalAlignment(),
               this.anchor
                  .getVerticalAlignment()
            ),
            true
         );
      } else if (this.anchor == HudAnchor.BOTTOM_LEFT
         || this.anchor == HudAnchor.BOTTOM_RIGHT) {
         widget.setY(
            ((HudWidgetRenderer)this.widgets.get(widgetIndex - 1))
                  .getY()
               - size[1],
            true
         );
      }
   }

   private float calculateX(float width, HudHorizontalAlignment horizontalAlignment) {
      HUDModule hud = hud();
      if (hud == null) {
         return 0.0F;
      }
      return switch (horizontalAlignment) {
         case LEFT -> hud.getSafeX();
         case RIGHT -> ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth() - width - hud.getSafeX();
         case CENTER -> ClientAccess.collectiveMinecraft.getWindow().getGuiScaledWidth() / 2.0F - width / 2.0F;
         default -> throw new IllegalStateException("Unexpected value: " + horizontalAlignment);
      };
   }

   private void applyHudTranslation(PoseStack poseStack) {
      HUDModule hud = hud();
      if (hud == null) {
         return;
      }
      poseStack.translate(
         hud.getParallaxOffset()
      );
   }

   public HudAnchor getAnchor() {
      return this.anchor;
   }

}
