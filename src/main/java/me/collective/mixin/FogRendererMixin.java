package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.SkyboxModule;
import java.awt.Color;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
   private static FogParameters applyNoRenderFogSettings(
      FogParameters fog, @Local(argsOnly = true, ordinal = 0) float viewDistance
   ) {
      NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
      if (noRenderModule != null
         && noRenderModule.isToggled()
         && noRenderModule.fogSetting.getValue()) {
         fog = noRenderModule.applyFogSettings(fog, (int) viewDistance);
      }

      SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
      if (skyboxModule != null
         && skyboxModule.isToggled()
         && skyboxModule.isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule.fogSetting.getValue();
         float red = configuredColor.getRed() / 255.0F;
         float green = configuredColor.getGreen() / 255.0F;
         float blue = configuredColor.getBlue() / 255.0F;
         float alpha = configuredColor.getAlpha() / 255.0F;
         fog = new FogParameters(fog.start(), fog.end(), fog.shape(), red, green, blue, alpha);
      }
      return fog;
   }

   @ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
   private static Vector4f applySkyboxFogColor(Vector4f fogColor) {
      SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
      if (skyboxModule != null
         && skyboxModule.isToggled()
         && skyboxModule.isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule.fogSetting.getValue();
         fogColor.set(
            configuredColor.getRed() / 255.0F,
            configuredColor.getGreen() / 255.0F,
            configuredColor.getBlue() / 255.0F,
            configuredColor.getAlpha() / 255.0F
         );
      }
      return fogColor;
   }
}
