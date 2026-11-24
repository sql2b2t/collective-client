package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.collective.client.ClientContext;
import me.collective.client.render.ColorUtils;
import me.collective.client.modules.render.SkyboxModule;
import java.awt.Color;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {
   private static SkyboxModule skyboxModule;
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }

   @ModifyReturnValue(method = "getBaseColor", at = @At("RETURN"))
   private int blendConfiguredFogColor(int originalColor) {
      if (skyboxModule().isToggled()
         && skyboxModule().isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule().backgroundSetting.getValue();
         Color baseColor = new Color(originalColor, true);
         return ColorUtils.blend(
               configuredColor, baseColor, configuredColor.getAlpha() / 255.0F
            )
            .hashCode();
      } else {
         return originalColor;
      }
   }
}
