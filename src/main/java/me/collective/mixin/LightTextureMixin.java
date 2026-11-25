package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.ClientContext;
import me.collective.client.render.ClientRenderPipelines;
import java.awt.Color;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderProgram;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightTextureMixin {
   private static AmbienceModule ambienceModule;
   private static AmbienceModule ambienceModule() {
      if (ambienceModule == null) {
         ambienceModule = ClientContext.getModule(AmbienceModule.class);
      }
      return ambienceModule;
   }
   @Unique
   private static final Vector3f lightmapTint = new Vector3f();

   @ModifyExpressionValue(
      method = "updateLightTexture",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/CoreShaders;LIGHTMAP:Lnet/minecraft/client/renderer/ShaderProgram;")
   )
   private ShaderProgram overrideLightmapPipeline(ShaderProgram originalPipeline) {
      if (ambienceModule().isToggled()
         && ambienceModule().brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN) {
         return ClientRenderPipelines.AMBIENCE;
      } else {
         return ambienceModule().isToggled()
               && ambienceModule().occlusionSetting.getValue()
               && ambienceModule().occlusionSetting
                  .isVisible()
            ? ClientRenderPipelines.OCCLUSION
            : originalPipeline;
      }
   }

   @ModifyExpressionValue(
      method = "updateLightTexture",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Lnet/minecraft/client/renderer/ShaderProgram;)Lnet/minecraft/client/renderer/CompiledShaderProgram;"
      )
   )
   private CompiledShaderProgram overrideLightmapTint(CompiledShaderProgram shader) {
      if (shader == null) {
         return null;
      }
      boolean tintEnabled = ambienceModule().brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN
         || ambienceModule().occlusionSetting.getValue()
            && ambienceModule().occlusionSetting
               .isVisible();
      Color tintColor = ambienceModule().brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN
         ? ambienceModule().colorSetting.getValue()
         : ambienceModule().occlusionColorSetting.getValue();
      if (ambienceModule().isToggled() && tintEnabled) {
         shader.safeGetUniform("AmbienceColor").set(
            lightmapTint.set(
                  tintColor.getRed(), tintColor.getGreen(), tintColor.getBlue()
               )
               .div(255.0F)
         );
      }
      return shader;
   }

   @ModifyReturnValue(method = "pack", at = @At("RETURN"))
   private static int overridePackedLight(int originalLight) {
      if (ambienceModule().isToggled()
         && ambienceModule().brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN) {
         return 0;
      } else {
         return ambienceModule().isToggled()
               && ambienceModule().occlusionSetting
                  .isVisible()
               && ambienceModule().occlusionSetting.getValue()
               && originalLight == 15728880
            ? 15728864
            : originalLight;
      }
   }
}
