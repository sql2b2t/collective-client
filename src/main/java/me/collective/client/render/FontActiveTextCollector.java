package me.collective.client.render;

import me.collective.client.ClientContext;
import me.collective.client.modules.client.FontsModule;

import me.collective.mixin.GuiGraphicsAccessor;
import java.util.function.Consumer;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.HoveredTextEffects;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public class FontActiveTextCollector implements Consumer<Style>, ActiveTextCollector {
   private static FontsModule fontsModule;
   private static FontsModule fontsModule() {
      if (fontsModule == null) {
         fontsModule = (FontsModule)ClientContext.getModule(FontsModule.class);
      }
      return fontsModule;
   }
   public static boolean customChatTextEnabled;
   private Parameters parameters;
   private final GuiGraphicsAccessor graphicsAccessor;
   private final HoveredTextEffects hoveredTextEffects;
   private final Consumer<Style> styleConsumer;

   @Override
   public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
      boolean checkHoveredStyle = this.hoveredTextEffects.allowCursorChanges
         || this.hoveredTextEffects.allowTooltip
         || this.styleConsumer != null;
      float left = alignedX(alignment, x, text);
      float width = FontRenderer.INSTANCE
         .getWidth(text);
      float height = FontRenderer.INSTANCE
         .getHeight();
      if (checkHoveredStyle) {
         float mouseX = this.graphicsAccessor.collective$mouseX();
         float mouseY = this.graphicsAccessor.collective$mouseY();
         visitHoveredStyle(
            text, mouseX, mouseY, left, y, width, height, parameters, this
         );
      }

      FontsModule fontsModule = fontsModule();
      if (fontsModule != null && ARGB.as8BitChannel(parameters.opacity()) != 0) {
         int color = ARGB.color(parameters.opacity(), -1);
         fontsModule.enqueueText(
            parameters.pose(), text, left, y, color, true
         );
      }
   }

   @Override
   public void defaultParameters(Parameters parameters) {
      this.parameters = parameters;
   }

   @Override
   public Parameters defaultParameters() {
      return this.parameters;
   }

   @Override
   public void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom, Parameters parameters) {
      int width = (int)FontRenderer.INSTANCE
         .getWidth(text.getVisualOrderText());
      int height = FontRenderer.INSTANCE
         .getHeight();
      this.defaultScrollingHelper(text, centerX, left, right, top, bottom, width, height, parameters);
   }

   public FontActiveTextCollector(Parameters parameters, GuiGraphics graphics, HoveredTextEffects hoveredTextEffects, Consumer<Style> styleConsumer) {
      this.parameters = parameters;
      this.graphicsAccessor = (GuiGraphicsAccessor)graphics;
      this.hoveredTextEffects = hoveredTextEffects;
      this.styleConsumer = styleConsumer;
   }

   static {
      customChatTextEnabled = false;
   }

   static void visitHoveredStyle(
      FormattedCharSequence text, float mouseX, float mouseY, float left, float top, float width, float height, Parameters parameters, Consumer<Style> styleConsumer
   ) {
      ScreenRectangle scissor = parameters.scissor();
      if (scissor != null && (mouseX < scissor.left() || mouseX >= scissor.right() || mouseY < scissor.top() || mouseY >= scissor.bottom())) {
         return;
      }
      Vector2f localMouse = parameters.pose().invert(new Matrix3x2f()).transformPosition(new Vector2f(mouseX, mouseY));
      if (!containsPoint(localMouse.x(), localMouse.y(), left, top, left + width, top + height)) {
         return;
      }
      float relativeX = localMouse.x() - left;
      float relativeY = localMouse.y() - top;
      if (relativeY < 0.0F || relativeY >= height) {
         return;
      }
      float[] advance = new float[]{0.0F};
      text.accept(
         (index, style, codePoint) -> {
            float glyphWidth = FontRenderer.INSTANCE
               .getWidth(
                  FormattedCharSequence.codepoint(codePoint, Style.EMPTY)
               );
            if (relativeX >= advance[0] && relativeX < advance[0] + glyphWidth) {
               styleConsumer.accept(style);
               return false;
            } else {
               advance[0] += glyphWidth;
               return true;
            }
         }
      );
   }

   private static float alignedX(
      TextAlignment alignment, float x, FormattedCharSequence text
   ) {
      float width = FontRenderer.INSTANCE.getWidth(text);
      return switch (alignment) {
         case LEFT -> x;
         case CENTER -> x - width / 2.0F;
         case RIGHT -> x - width;
      };
   }

   private static boolean containsPoint(
      float x, float y, float left, float top, float right, float bottom
   ) {
      return x >= left && x < right && y >= top && y < bottom;
   }

   @Override
   public void accept(Style style) {
      if (this.hoveredTextEffects.allowTooltip && style.getHoverEvent() != null) {
         this.graphicsAccessor.collective$hoverStyle(style);
      }

      if (this.hoveredTextEffects.allowCursorChanges && style.getClickEvent() != null) {
         this.graphicsAccessor.collective$clickStyle(style);
      }

      if (this.styleConsumer != null) {
         this.styleConsumer.accept(style);
      }
   }

   public static class ClickableStyleFinder implements ActiveTextCollector {
      private static final Parameters DEFAULT_PARAMETERS;
      private final float mouseX;
      private final float mouseY;
      private final boolean includeInsertions;
      private Parameters parameters = DEFAULT_PARAMETERS;
      private Style result;
      private final Consumer<Style> clickableStyleConsumer;

      public ClickableStyleFinder(double mouseX, double mouseY, boolean includeInsertions) {
         this.includeInsertions = includeInsertions;
         this.clickableStyleConsumer = style -> {
            if (style.getClickEvent() != null
               || this.includeInsertions && style.getInsertion() != null) {
               this.result = style;
            }
         };
         this.mouseX = (float)mouseX;
         this.mouseY = (float)mouseY;
      }

      @Override
      public Parameters defaultParameters() {
         return this.parameters;
      }

      @Override
      public void defaultParameters(Parameters parameters) {
         this.parameters = parameters;
      }

      @Override
      public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
         float left = FontActiveTextCollector.alignedX(alignment, x, text);
         FontActiveTextCollector.visitHoveredStyle(
            text,
            this.mouseX,
            this.mouseY,
            left,
            y,
            FontRenderer.INSTANCE
               .getWidth(text),
            FontRenderer.INSTANCE
               .getHeight(),
            parameters,
            this.clickableStyleConsumer
         );
      }

      @Override
      public void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom, Parameters parameters) {
         int width = (int)FontRenderer.INSTANCE
            .getWidth(text);
         int height = FontRenderer.INSTANCE
            .getHeight();
         this.defaultScrollingHelper(text, centerX, left, right, top, bottom, width, height, parameters);
      }

      public Style result() {
         return this.result;
      }

      static {
         DEFAULT_PARAMETERS = new Parameters(new Matrix3x2f());
      }

   }
}
