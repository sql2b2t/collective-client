package me.collective.client.render;

import me.collective.client.utils.math.MathConstants;
import me.collective.client.modules.client.UIModule;
import me.collective.client.utils.math.MathUtils;

import java.awt.Color;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ColorUtils {
   public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

   public static Color getLatencyColor(int latency) {
      float progress = MathUtils.rangeProgress(latency, 30.0F, 150.0F);
      return progress <= 0.5F ? new Color(progress * 2.0F, 1.0F, 0.0F) : new Color(0.8F, 1.0F - (progress - 0.5F) * 2.0F, 0.0F);
   }

   public static int getHealthColor(double healthRatio) {
      float progress = (float)Mth.clamp(healthRatio - 0.20000000298023224D, 0.0D, 1.0D);
      float brightness = 1.0F;
      if (healthRatio <= 0.20000000298023224D) {
         brightness = (float)Mth.clamp(healthRatio / 0.20000000298023224D, 0.5D, 1.0D);
      }
      float red = Mth.clamp(2.0F * (1.0F - progress), 0.3F, 1.0F) * brightness;
      float green = Math.min(2.0F * progress, 1.0F) * brightness;
      float blue = 0.3F * brightness;
      return ARGB.colorFromFloat(1.0F, red, green, blue);
   }

   public static int modulateWithShaderColor(int argb) {
      return modulateArgb(
         argb, RenderUtils.getRenderColor()
      );
   }

   public static Color fromArgb(int argb) {
      return new Color(argb, true);
   }

   public static Color getThemeColor() {
      return UIModule.INSTANCE.colorSetting.getValue();
   }

   public static Color parseHexColor(String text) {
      if (text.startsWith("#")) {
         text = text.substring(1);
      }

      int length = text.length();
      if (length == 3) {
         char[] characters = text.toCharArray();
         text = ""
            + characters[0]
            + characters[0]
            + characters[1]
            + characters[1]
            + characters[2]
            + characters[2];
         length = 6;
      }

      if (length != 6 && length != 8) {
         throw new IllegalArgumentException();
      }

      boolean hasAlpha = length == 8;
      int[] channels = new int[hasAlpha ? 4 : 3];
      for (int index = 0; index < (hasAlpha ? 4 : 3); index++) {
         channels[index] = Integer.parseInt(text.substring(index * 2, (index + 1) * 2), 16);
      }

      int channelOffset = hasAlpha ? 1 : 0;
      return new Color(
         channels[channelOffset],
         channels[1 + channelOffset],
         channels[2 + channelOffset],
         hasAlpha ? channels[0] : 255
      );
   }

   private ColorUtils() {
   }

   public static Vec3 toRgbVector(Color color) {
      return new Vec3(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
   }

   public static Color darken(Color color, float factor) {
      return new Color(
         Math.max((int)(color.getRed() * factor), 0), Math.max((int)(color.getGreen() * factor), 0), Math.max((int)(color.getBlue() * factor), 0), color.getAlpha()
      );
   }

   private static String toHexComponent(int channel) {
      return (channel < 16 ? "0" : "") + Integer.toHexString(channel);
   }

   public static String formatRgb(Color color, boolean includeAlpha) {
      if (!includeAlpha) {
         return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
      } else {
         return "rgba(%d, %d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
      }
   }

   public static Color pulse(
      Color firstColor, Color secondColor, double period, double offset
   ) {
      if (period <= 0.0) {
         return firstColor;
      }

      double phase = (System.currentTimeMillis() + offset) % period / period;
      return phase > MathConstants.HALF
         ? blend(
            secondColor,
            firstColor,
            (float)((phase - MathConstants.HALF) * 2.0)
         )
         : blend(firstColor, secondColor, (float)(phase * 2.0));
   }

   public static Color rainbow(
      int offset, float saturation, float brightness, int alpha
   ) {
      return rainbow(
         MathConstants.FULL_TURN_DEGREES, offset, saturation, brightness, alpha
      );
   }

   public static int modulateArgb(
      int argb, int redMultiplier, int greenMultiplier, int blueMultiplier, int alphaMultiplier
   ) {
      int alpha = (argb >>> 24 & 255) * alphaMultiplier >> 8;
      int red = (argb >>> 16 & 255) * redMultiplier >> 8;
      int green = (argb >>> 8 & 255) * greenMultiplier >> 8;
      int blue = (argb & 255) * blueMultiplier >> 8;
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   public static int modulateArgb(int argb, int[] multipliers) {
      return modulateArgb(
         argb,
         multipliers[0],
         multipliers[1],
         multipliers[2],
         multipliers[3]
      );
   }

   public static Color rainbow(
      int period, int offset, float saturation, float brightness, int alpha
   ) {
      if (period <= 0) {
         return withAlpha(Color.getHSBColor(0.0F, saturation, brightness), alpha);
      }

      double phase = Math.ceil((System.currentTimeMillis() + offset) / 20.0);
      phase %= period;
      Color color = Color.getHSBColor((float)(phase / period), saturation, brightness);
      return withAlpha(color, alpha);
   }

   public static int packArgb(
      int red, int green, int blue, int alpha
   ) {
      return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
   }

   public static Color blend(Color firstColor, Color secondColor, float firstWeight) {
      try {
         return new Color(
            (int)(firstColor.getRed() * firstWeight + secondColor.getRed() * (1.0F - firstWeight)),
            (int)(firstColor.getGreen() * firstWeight + secondColor.getGreen() * (1.0F - firstWeight)),
            (int)(firstColor.getBlue() * firstWeight + secondColor.getBlue() * (1.0F - firstWeight)),
            (int)(firstColor.getAlpha() * firstWeight + secondColor.getAlpha() * (1.0F - firstWeight))
         );
      } catch (IllegalArgumentException exception) {
         exception.printStackTrace();
         return Color.white;
      }
   }

   public static Color withAlpha(Color color, float alpha) {
      return withAlpha(color, Math.round(alpha * 255.0F));
   }

   public static int packArgb(
      float red, float green, float blue, float alpha
   ) {
      return ((int)(alpha * 255.0F) & 255) << 24
         | ((int)(red * 255.0F) & 255) << 16
         | ((int)(green * 255.0F) & 255) << 8
         | (int)(blue * 255.0F) & 255;
   }

   public static Color brighten(Color color, float factor) {
      int red = color.getRed();
      int green = color.getGreen();
      int blue = color.getBlue();
      int alpha = color.getAlpha();
      int minimumChannel = Mth.clamp((int)(1.0 / (1.0 - factor)), 0, 255);
      if (red == 0 && green == 0 && blue == 0) {
         return new Color(minimumChannel, minimumChannel, minimumChannel, alpha);
      }

      if (red > 0 && red < minimumChannel) {
         red = minimumChannel;
      }

      if (green > 0 && green < minimumChannel) {
         green = minimumChannel;
      }

      if (blue > 0 && blue < minimumChannel) {
         blue = minimumChannel;
      }

      return new Color(
         Mth.clamp((int)(red / factor), 0, 255),
         Mth.clamp((int)(green / factor), 0, 255),
         Mth.clamp((int)(blue / factor), 0, 255),
         alpha
      );
   }

   public static Color redToGreen(float progress) {
      return new Color(Math.min((1.0F - progress) * 2.0F, 1.0F), Math.min(progress * 2.0F, 1.0F), 0.0F);
   }

   public static String toHex(Color color, boolean includeHash) {
      StringBuilder hex = new StringBuilder();
      hex.append(toHexComponent(color.getAlpha()));
      hex.append(toHexComponent(color.getRed()));
      hex.append(toHexComponent(color.getGreen()));
      hex.append(toHexComponent(color.getBlue()));
      return (includeHash ? "#" : "") + hex;
   }

   public static Color withAlpha(Color color, int alpha) {
      return new Color(
         color.getRed(),
         color.getGreen(),
         color.getBlue(),
         Mth.clamp(alpha, 0, 255)
      );
   }

   public static int toArgb(Color color, float alpha) {
      return packArgb(
         color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp((int)(alpha * 255.0F), 0, 255)
      );
   }

   public static int modulateRgb(
      int argb, int redMultiplier, int greenMultiplier, int blueMultiplier
   ) {
      int alpha = argb >>> 24 & 255;
      int red = (argb >>> 16 & 255) * redMultiplier >> 8;
      int green = (argb >>> 8 & 255) * greenMultiplier >> 8;
      int blue = (argb & 255) * blueMultiplier >> 8;
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   public static Color transparent(Color color) {
      return withAlpha(color, 0);
   }

   public static Color multiplyAlpha(Color color, float factor) {
      return withAlpha(color, (int)(color.getAlpha() * factor));
   }

   public static int toArgb(Color color, int alpha) {
      return packArgb(
         color.getRed(), color.getGreen(), color.getBlue(), alpha
      );
   }

}
