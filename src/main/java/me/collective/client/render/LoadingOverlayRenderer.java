package me.collective.client.render;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class LoadingOverlayRenderer {

   public static final int BACKGROUND_RGB = 1644825;
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
      "collective", "textures/collective.png"
   );

   private LoadingOverlayRenderer() {
   }

   public static void render(
      GuiGraphics graphics, float opacity, float backgroundOpacity
   ) {
      render(
         graphics,
         opacity,
         backgroundOpacity,
         0.5F
      );
   }

   public static void render(
      GuiGraphics graphics, float opacity, float backgroundOpacity, float logoScale
   ) {
      opacity = Mth.clamp(
         opacity,
         0.0F,
         1.0F
      );
      int logoX = (int)(
         (
               GuiScaleUtils.getScaledWidth()
                  - 256.0F
                     * logoScale
            )
            / 2.0F
      );
      int logoY = (int)(
         (
               GuiScaleUtils.getScaledHeight()
                  - 256.0F
                     * logoScale
            )
            / 2.0F
      );
      int backgroundArgb = ColorUtils.toArgb(
         new Color(1644825,
            false
         ),
         backgroundOpacity * opacity
      );
      int logoArgb = ColorUtils.toArgb(Color.white, opacity);
      graphics.fill(
         0,
         0,
         GuiScaleUtils.getScaledWidth(),
         GuiScaleUtils.getScaledHeight(),
         backgroundArgb
      );
      graphics.pose().pushMatrix();
      graphics.pose().translate(logoX, logoY);
      graphics.pose().scale(logoScale, logoScale);
      graphics.blit(
         RenderPipelines.GUI_TEXTURED,
         LOGO_TEXTURE,
         0,
         0,
         0.0F,
         0.0F,256,256,256,256,
         logoArgb
      );
      graphics.pose().popMatrix();
   }
}
