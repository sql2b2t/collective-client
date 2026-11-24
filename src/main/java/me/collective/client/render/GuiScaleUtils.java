package me.collective.client.render;

import com.mojang.blaze3d.platform.Window;
import me.collective.client.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class GuiScaleUtils implements ClientAccess {
   private static final boolean ADVANCED_UI_SCALE_LOADED = FabricLoader.getInstance().isModLoaded("advanced-ui-scale");
   private static double previousGuiScale = -1.0D;
   private static double scaleMultiplier = 1.0D;

   public static int getScaledHeight() {
      return (int)(collectiveMinecraft.getWindow().getGuiScaledHeight() / scaleMultiplier + 1.0D);
   }

   public static int getScaledWidth() {
      return (int)(collectiveMinecraft.getWindow().getGuiScaledWidth() / scaleMultiplier + 1.0D);
   }

   public static void restoreGuiScale() {
      if (previousGuiScale < 0.0D) {
         return;
      }
      collectiveMinecraft.getWindow().setGuiScale((int)previousGuiScale);
      previousGuiScale = -1.0D;
      scaleMultiplier = 1.0D;
   }

   public static boolean isAdvancedUiScaleLoaded() {
      return ADVANCED_UI_SCALE_LOADED;
   }

   public static double getScaleMultiplier() {
      return scaleMultiplier;
   }

   public static void applyDefaultGuiScale() {
      applyGuiScale(1.0F);
   }

   public static int calculateGuiScale() {
      Minecraft minecraft = collectiveMinecraft;
      return minecraft.getWindow().calculateScale(2, minecraft.isEnforceUnicode());
   }

   public static void applyGuiScale(float scale) {
      if (ADVANCED_UI_SCALE_LOADED) {
         return;
      }
      Window window = collectiveMinecraft.getWindow();
      if (previousGuiScale < 0.0D) {
         previousGuiScale = window.getGuiScale();
      }
      scaleMultiplier = scale;
      window.setGuiScale(calculateGuiScale());
   }

   public static double calculateScaleMultiplier(float scale) {
      return ADVANCED_UI_SCALE_LOADED ? 1.0D : (double)calculateGuiScale() / collectiveMinecraft.getWindow().getGuiScale() * scale;
   }

   private GuiScaleUtils() {
   }
}
