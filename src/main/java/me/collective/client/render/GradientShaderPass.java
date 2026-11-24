package me.collective.client.render;

import me.collective.client.modules.render.ShaderModule.TypeChoice;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.ShaderModule;
import me.collective.client.settings.Setting;

import java.awt.Color;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class GradientShaderPass extends PreprocessedShaderPass {
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
      ShaderUniforms capturedProgramForLineWidth = this.uniforms;

      ShaderModule capturedShaderForHandOutline = shaderModule;
      int lineWidth;
      if (capturedShaderForHandOutline.shouldSkipHandOutline()) {
         lineWidth = 0;
      } else {
         lineWidth = (shaderModule.lineWidthSetting.getValue());
      }
      capturedProgramForLineWidth.set("u_Width", lineWidth);
      ShaderUniforms capturedProgramForFastLinesEnabled = this.uniforms;

      boolean fastLinesEnabled = (shaderModule.fastLinesSetting.getValue())
         || ClientContext.shaderRegistry.renderingHands;
      capturedProgramForFastLinesEnabled.set("u_FastLines", fastLinesEnabled);
      ShaderUniforms capturedProgramForGlowQuality = this.uniforms;

      int glowQuality = (shaderModule.qualitySetting.getValue());
      capturedProgramForGlowQuality.set("u_GlowQuality", glowQuality);
      ShaderUniforms capturedProgramForInnerGlowEnabled = this.uniforms;

      boolean innerGlowEnabled = (shaderModule.innerSetting.getValue());
      capturedProgramForInnerGlowEnabled.set("u_GlowInner", innerGlowEnabled);
      ShaderUniforms capturedProgramForSmoothGradient = this.uniforms;

      boolean smoothGradient = (shaderModule.smoothSetting.getValue());
      capturedProgramForSmoothGradient.set("u_Smooth", smoothGradient);
      ShaderUniforms capturedProgramForAnimationTime = this.uniforms;

      float elapsedMillis = (float)(System.currentTimeMillis() - START_TIME_MILLIS);

      float scaledAnimationTime = elapsedMillis * 0.01F;
      Setting<Float> gradientSpeedOption = shaderModule.speedSetting;
      Float gradientSpeedValue = gradientSpeedOption.getValue();
      float speedScaledTime = scaledAnimationTime * gradientSpeedValue;

      float animationTime = speedScaledTime * 0.15F;
      capturedProgramForAnimationTime.set("u_Time", animationTime);
      ShaderModule capturedShaderForHandFill = shaderModule;
      if (capturedShaderForHandFill.shouldSkipHandFill()) {
         ShaderUniforms capturedProgramForFillColor = this.uniforms;

         Setting<Color> fillColorOption = shaderModule.fillSetting;
         Color fillColor = ColorUtils.transparent(
            fillColorOption.getValue()
         );
         capturedProgramForFillColor.set("u_FillColor", fillColor);
         ShaderUniforms capturedProgramForMiddleFillColor = this.uniforms;

         Setting<Color> secondFillColorOption = shaderModule.fillSecondSetting;
         Color middleFillColor = ColorUtils.transparent(
            secondFillColorOption.getValue()
         );
         capturedProgramForMiddleFillColor.set("u_FillColor2", middleFillColor);
         ShaderUniforms capturedProgramForEndFillColor = this.uniforms;

         Setting<Color> secondFillColorOption2 = shaderModule.fillSecondSetting;
         Color endFillColor = ColorUtils.transparent(
            secondFillColorOption2.getValue()
         );
         capturedProgramForEndFillColor.set("u_FillColor3", endFillColor);
      } else {
         ShaderUniforms capturedProgramForFillColor2 = this.uniforms;

         ShaderModule capturedShaderForClusterAlpha = shaderModule;
         Setting<Color> fillColorOption2 = shaderModule.fillSetting;
         Color baseFillColor = fillColorOption2.getValue();

         Color fillColor2 = capturedShaderForClusterAlpha.applyClusterAlpha(baseFillColor);
         capturedProgramForFillColor2.set("u_FillColor", fillColor2);
         ShaderUniforms capturedProgramForMiddleFillColor2 = this.uniforms;

         ShaderModule capturedShaderForClusterAlpha2 = shaderModule;
         Setting<Color> secondFillColorOption3 = shaderModule.fillSecondSetting;
         Setting<Color> fillColorOption3 = shaderModule.fillSetting;
         ShaderModule shaderForColorBlend = shaderModule;
         Color blendedFillColor = shaderForColorBlend.blendSettingColors(fillColorOption3, secondFillColorOption3);

         Color middleFillColor2 = capturedShaderForClusterAlpha2.applyClusterAlpha(blendedFillColor);
         capturedProgramForMiddleFillColor2.set("u_FillColor2", middleFillColor2);
         ShaderUniforms capturedProgramForEndFillColor2 = this.uniforms;

         ShaderModule capturedShaderForClusterAlpha3 = shaderModule;
         Setting<Color> secondFillColorOption4 = shaderModule.fillSecondSetting;
         Color endFillColorValue = secondFillColorOption4.getValue();

         Color endFillColor2 = capturedShaderForClusterAlpha3.applyClusterAlpha(endFillColorValue);
         capturedProgramForEndFillColor2.set("u_FillColor3", endFillColor2);
      }

      ShaderUniforms capturedProgramForOutlineColor = this.uniforms;

      Setting<Color> outlineColorOption = shaderModule.outlineColorSetting;
      Color outlineColor = outlineColorOption.getValue();
      capturedProgramForOutlineColor.set("u_OutlineColor", outlineColor);
      ShaderUniforms capturedProgramForMiddleOutlineColor = this.uniforms;

      ShaderModule capturedShaderForClusterAlpha4 = shaderModule;
      Setting<Color> secondOutlineColorOption = shaderModule.outlineSecondSetting;
      Setting<Color> outlineColorOption2 = shaderModule.outlineColorSetting;
      ShaderModule shaderForColorBlend2 = shaderModule;
      Color blendedOutlineColor = shaderForColorBlend2.blendSettingColors(outlineColorOption2, secondOutlineColorOption);

      Color middleOutlineColor = capturedShaderForClusterAlpha4.applyClusterAlpha(blendedOutlineColor);
      capturedProgramForMiddleOutlineColor.set("u_OutlineColor2", middleOutlineColor);
      ShaderUniforms capturedProgramForEndOutlineColor = this.uniforms;

      Setting<Color> secondOutlineColorOption2 = shaderModule.outlineSecondSetting;
      Color endOutlineColor = secondOutlineColorOption2.getValue();
      capturedProgramForEndOutlineColor.set("u_OutlineColor3", endOutlineColor);
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
      ShaderUniforms capturedProgramForGradientStep = this.uniforms;

      Setting<Float> stepOption = shaderModule.stepSetting;
      Float stepValue = stepOption.getValue();
      float step = stepValue;

      float gradientStep = step * 4.0F;
      capturedProgramForGradientStep.set("u_Step", gradientStep);
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

   public GradientShaderPass() {
      super("base.vert", "gradient.frag", COVERAGE_PASS);

      this.initializeProgram();
   }

}
