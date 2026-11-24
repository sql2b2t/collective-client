package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin implements ClientAccess {
   private static AmbienceModule ambienceModule;
   private static AmbienceModule ambienceModule() {
      if (ambienceModule == null) {
         ambienceModule = ClientContext.getModule(AmbienceModule.class);
      }
      return ambienceModule;
   }

   @ModifyExpressionValue(method = "renderInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
   private int collective$applyWeatherOpacity(int originalColor) {
      if (ambienceModule().isToggled()
         && ambienceModule().worldWeatherSetting.getValue()) {
         float opacityMultiplier = (ambienceModule().opacitySetting.getValue())
               .intValue()
            / 100.0F;
         int adjustedAlpha = (int)(ARGB.alpha(originalColor) * opacityMultiplier) & 0xFF;
         return adjustedAlpha << 24 | originalColor & 0xFFFFFF;
      } else {
         return originalColor;
      }
   }

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")
   )
   private int collective$forceWeatherBelowSurface(int originalHeight) {
      return ambienceModule().isToggled()
            && ambienceModule().worldWeatherSetting.getValue()
            && ambienceModule().weatherAlwaysSetting.getValue()
            && (ambienceModule().weatherSetting.getValue()).supportsPrecipitation()
         ? Integer.MIN_VALUE
         : originalHeight;
   }

   @ModifyExpressionValue(
      method = "getPrecipitationAt",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;")
   )
   private Precipitation collective$overridePrecipitation(Precipitation originalPrecipitation) {
      Precipitation configuredPrecipitation = (ambienceModule().weatherSetting
            .getValue())
         .getPrecipitation();
      return ambienceModule().isToggled()
            && ambienceModule().worldWeatherSetting.getValue()
            && configuredPrecipitation != null
         ? configuredPrecipitation
         : originalPrecipitation;
   }
}
