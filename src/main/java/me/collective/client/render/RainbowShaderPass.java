package me.collective.client.render;

import me.collective.client.modules.render.ShaderModule.TypeChoice;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.ShaderModule;
import me.collective.client.settings.Setting;

import java.awt.Color;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class RainbowShaderPass extends PreprocessedShaderPass {
   @Override
   public void updateUniforms() {
      super.updateUniforms();

      ShaderModule shaderModule = shaderModule();
      if (shaderModule == null) {
         return;
      }

      GL13.glActiveTexture(GL13.GL_TEXTURE7);

      int overlayTextureId = PostProcessShaderPass.overlayTextureId;

      GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTextureId);
      ShaderUniforms capturedProgramForImageEnabled = this.uniforms;

      boolean imageEnabled = (shaderModule.imageSetting.getValue());
      capturedProgramForImageEnabled.set("u_Image", imageEnabled);
      ShaderUniforms capturedProgramForOverlaySampler = this.uniforms;

      capturedProgramForOverlaySampler.set("u_Overlay", 7);
      ShaderUniforms capturedProgramForOverlayAlpha = this.uniforms;

      ShaderModule capturedShaderForOverlayAlpha = shaderModule;
      float overlayAlpha = capturedShaderForOverlayAlpha.getOverlayAlpha();
      capturedProgramForOverlayAlpha.set("u_OverlayAlpha", overlayAlpha);
      ShaderUniforms capturedProgramForFillColor = this.uniforms;

      ShaderModule capturedShaderForHandFill = shaderModule;
      Color selectedFillColor;
      if (capturedShaderForHandFill.shouldSkipHandFill()) {
         Setting<Color> outlineColorOption = shaderModule.outlineColorSetting;
         selectedFillColor = ColorUtils.transparent(outlineColorOption.getValue());
      } else {
         ShaderModule capturedShaderForClusterAlpha = shaderModule;
         Color fillColorValue = shaderModule.fillSetting.getValue();

         selectedFillColor = capturedShaderForClusterAlpha.applyClusterAlpha(fillColorValue);
      }

      Color fillColor = selectedFillColor;
      capturedProgramForFillColor.set("u_Fill", fillColor);
      ShaderUniforms capturedProgramForRainbowOffset = this.uniforms;

      float elapsedMillis = (float)(System.currentTimeMillis() - START_TIME_MILLIS);

      float scaledAnimationTime = elapsedMillis * 0.001F;
      Float rainbowSpeedValue = shaderModule.rainbowSpeedSetting.getValue();
      float rainbowOffset = scaledAnimationTime * rainbowSpeedValue;
      capturedProgramForRainbowOffset.set("u_Offset", rainbowOffset);
      ShaderUniforms capturedProgramForFillStrength = this.uniforms;

      float fillStrength = (shaderModule.fillStrengthSetting.getValue());
      capturedProgramForFillStrength.set("u_Strength", fillStrength);
      ShaderUniforms capturedProgramForOutlineStrength = this.uniforms;

      float outlineStrength = (shaderModule.lineStrengthSetting.getValue());
      capturedProgramForOutlineStrength.set("u_StrengthLine", outlineStrength);
      ShaderUniforms capturedProgramForOutlineColor = this.uniforms;

      Color outlineColor = shaderModule.outlineColorSetting.getValue();
      capturedProgramForOutlineColor.set("u_Outline", outlineColor);
      ShaderUniforms capturedProgramForLineWidth = this.uniforms;

      ShaderModule capturedShaderForHandOutline = shaderModule;
      int lineWidth;
      if (capturedShaderForHandOutline.shouldSkipHandOutline()) {
         lineWidth = 0;
      } else {
         lineWidth = (shaderModule.lineWidthSetting.getValue());
      }
      capturedProgramForLineWidth.set("u_Width", lineWidth);
      ShaderUniforms capturedProgramForGlowQuality = this.uniforms;

      Integer glowQualityValue = shaderModule.qualitySetting.getValue();
      int glowQuality = glowQualityValue;
      capturedProgramForGlowQuality.set("u_GlowQuality", glowQuality);
      ShaderUniforms capturedProgramForInnerGlowEnabled = this.uniforms;

      Setting<Boolean> innerGlowOption = shaderModule.innerSetting;
      Boolean innerGlowValue = innerGlowOption.getValue();
      boolean innerGlowEnabled = innerGlowValue;
      capturedProgramForInnerGlowEnabled.set("u_GlowInner", innerGlowEnabled);
      ShaderUniforms capturedProgramForFastLinesEnabled = this.uniforms;

      Setting<Boolean> fastLinesOption = shaderModule.fastLinesSetting;
      Boolean fastLinesValue = fastLinesOption.getValue();
      boolean fastLinesEnabled = fastLinesValue
         || ClientContext.shaderRegistry.renderingHands;
      capturedProgramForFastLinesEnabled.set("u_FastLines", fastLinesEnabled);
      ShaderUniforms capturedProgramForDecoratorKind = this.uniforms;

      Setting<TypeChoice> decoratorTypeOption = shaderModule.typeSetting;
      ShaderModule.TypeChoice decoratorTypeValue = decoratorTypeOption.getValue();
      int decoratorKind = decoratorTypeValue.ordinal();
      capturedProgramForDecoratorKind.set("u_Dots", decoratorKind);
      ShaderUniforms capturedProgramForDecoratorRadius = this.uniforms;

      Setting<Integer> decoratorRadiusOption = shaderModule.radiusSetting;
      Integer decoratorRadiusValue = decoratorRadiusOption.getValue();
      int decoratorRadius = decoratorRadiusValue;
      capturedProgramForDecoratorRadius.set("u_DotsRadius", decoratorRadius);
      ShaderUniforms capturedProgramForDecoratorAlpha = this.uniforms;

      Setting<Float> decoratorAlphaOption = shaderModule.alphaSetting;
      Float decoratorAlphaValue = decoratorAlphaOption.getValue();
      float decoratorAlpha = decoratorAlphaValue;
      capturedProgramForDecoratorAlpha.set("u_DotsAlpha", decoratorAlpha);
      ShaderUniforms capturedProgramForGlowWeight = this.uniforms;

      Setting<Float> glowWeightOption = shaderModule.weightSetting;
      Float glowWeightValue = glowWeightOption.getValue();
      float glowWeight = glowWeightValue;
      capturedProgramForGlowWeight.set("u_Glow", glowWeight);
      ShaderUniforms capturedProgramForFadeEnabled = this.uniforms;

      Setting<Float> fadeOption = shaderModule.fadeSetting;

      boolean fadeEnabled = !fadeOption.isAtMaximum();
      capturedProgramForFadeEnabled.set("u_Fade", fadeEnabled);
   }

   public RainbowShaderPass() {
      super("base.vert", "rainbow.frag", COVERAGE_PASS);

      this.initializeProgram();
   }

}
