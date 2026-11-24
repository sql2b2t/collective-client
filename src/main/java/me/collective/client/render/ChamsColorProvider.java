package me.collective.client.render;

import me.collective.client.managers.SocialRegistry;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.client.ColorsModule;
import me.collective.client.modules.render.ChamsModule;
import me.collective.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public interface ChamsColorProvider extends ClientAccess {
   Color TRANSPARENT_COLOR = ColorsBinding.createTransparentColor();

   default void applyColorOverride(Color[] colors, Color overrideColor) {
      Color outlineColor = colors[0];
      Color overrideOutline = ColorUtils.withAlpha(
         overrideColor, outlineColor.getAlpha()
      );
      Color fillColor = colors[1];
      EntityChamsRenderer.setColors(
         overrideOutline, ColorUtils.withAlpha(overrideColor, fillColor.getAlpha())
      );
   }

   default void renderEntity(
      ChamsModule module, Entity entity, PoseStack matrices
   ) {
      Color[] colors = this.getColors(module);
      Setting<Boolean> fadeSetting = module.fadeSetting;
      Boolean fadeEnabled = fadeSetting.getValue();
      if (fadeEnabled) {
         float distanceFade = module.getDistanceFade(entity);
         Color[] fadedColors = new Color[2];
         Color outlineColor = colors[0];
         Color outlineAlphaSource = colors[0];
         fadedColors[0] = ColorUtils.withAlpha(
            outlineColor, (int)(distanceFade * outlineAlphaSource.getAlpha())
         );
         Color fillColor = colors[1];
         Color fillAlphaSource = colors[1];
         fadedColors[1] = ColorUtils.withAlpha(
            fillColor, (int)(distanceFade * fillAlphaSource.getAlpha())
         );
         colors = fadedColors;
      }

      EntityChamsRenderer.setColors(colors[0], colors[1]);
      Setting<Boolean> friendsSetting = module.friendsSetting;
      Boolean friendsEnabled = friendsSetting.getValue();
      ColorsModule colorsModule = ColorsBinding.module();
      if (friendsEnabled && colorsModule != null && entity instanceof Player socialPlayer) {
         Player friendPlayer = socialPlayer;
         SocialRegistry friendRegistry = ClientContext.socialRegistry;
         if (friendRegistry.isFriendPlayer(friendPlayer)) {
            Setting<Color> friendColorSetting = colorsModule.friendColorSetting;
            Color friendColor = friendColorSetting.getValue();
            this.applyColorOverride(colors, friendColor);
         }

         SocialRegistry enemyRegistrySnapshot = ClientContext.socialRegistry;
         Player enemyPlayer = socialPlayer;
         if (enemyRegistrySnapshot.isEnemyPlayer(enemyPlayer)) {
            Setting<Color> enemyColorSetting = colorsModule.enemyColorSetting;
            Color enemyColor = enemyColorSetting.getValue();
            this.applyColorOverride(colors, enemyColor);
         }
      }

      if (entity instanceof Player animatedPlayer) {
         if (module.usesStaticPlayerAnimation()) {
            animatedPlayer.walkAnimation.speedOld = 0.0F;
            animatedPlayer.walkAnimation.speed = 0.0F;
            animatedPlayer.walkAnimation.position = 0.0F;
         }
      }

      EntityChamsRenderer.renderEntity(matrices, entity);
   }

   default Color[] getColors(ChamsModule module) {
      Color[] colors = new Color[2];
      Setting<Color> outlineSetting = module.outlineSetting;
      colors[0] = outlineSetting.getValue();
      Setting<Color> fillSetting = module.fillSetting;
      colors[1] = fillSetting.getValue();
      return colors;
   }

   default boolean isEnabled() {
      return false;
   }

   static void bindModuleRegistry(ModuleRegistry registry) {
      ColorsBinding.module = (ColorsModule)registry.getModule(ColorsModule.class);
   }

   final class ColorsBinding {
      private static ColorsModule module;
      private static ColorsModule module() {
         if (module == null) {
            module = (ColorsModule)ClientContext.getModule(ColorsModule.class);
         }
         return module;
      }

      private ColorsBinding() {
      }

      private static Color createTransparentColor() {
         return new Color(0, 0, 0, 0);
      }
   }

}
