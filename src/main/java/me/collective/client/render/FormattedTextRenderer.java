package me.collective.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

public final class FormattedTextRenderer implements FormattedCharSink {
   private final int brightness;
   private final FontAtlas atlas;
   private final int baseColor;
   private final PoseStack poses;
   private float cursorX;
   private float cursorY;

   public FormattedTextRenderer(PoseStack poses, int baseColor, float brightness, FontAtlas atlas) {
      this.poses = poses;
      this.baseColor = baseColor;
      this.brightness = (int)(brightness * 255.0F);
      this.atlas = atlas;
   }

   @Override
   public boolean accept(int index, Style style, int codePoint) {
      TextColor textColor = style.getColor();
      int color = textColor == null
         ? this.baseColor
         : ARGB.color(ARGB.alpha(this.baseColor), textColor.getValue());
      if (this.brightness != 255) {
         color = ColorUtils.modulateRgb(color, this.brightness, this.brightness, this.brightness);
      }
      this.cursorX += this.atlas.drawGlyph(
         this.poses, AtlasTextRenderer.glyphMesh, Character.toString(codePoint).charAt(0),
         this.cursorX, this.cursorY, color
      );
      return true;
   }

   int draw(FormattedCharSequence sequence, float x, float y) {
      if (sequence == null) {
         return 0;
      }
      this.cursorX = x * 4.0F;
      this.cursorY = y * 4.0F;
      this.poses.pushPose();
      try {
         this.poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
         this.poses.scale(0.25F, 0.25F, 1.0F);
         if (!AtlasTextRenderer.glyphMesh.isBuilding()) {
            AtlasTextRenderer.glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         }
         sequence.accept(this);
      } finally {
         this.poses.popPose();
      }
      return (int)(this.cursorX / 4.0F);
   }
}
