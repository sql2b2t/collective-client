package me.collective.client.render;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.ShaderModule;
import me.collective.client.settings.Setting;

import java.awt.Color;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class SolidShaderPass extends PreprocessedShaderPass {
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
      ShaderUniforms capturedProgramForShapeMode = this.uniforms;

      capturedProgramForShapeMode.set("u_ShapeMode", 2);
      ShaderUniforms capturedProgramForGlowWeight = this.uniforms;

      float glowWeight = (shaderModule.weightSetting.getValue());
      capturedProgramForGlowWeight.set("u_Glow", glowWeight);
      ShaderUniforms capturedProgramForFillColor = this.uniforms;

      ShaderModule capturedShaderForHandFill = shaderModule;
      Color selectedFillColor;
      if (capturedShaderForHandFill.shouldSkipHandFill()) {
         selectedFillColor = ColorUtils.TRANSPARENT;
      } else {
         ShaderModule capturedShaderForClusterAlpha = shaderModule;
         Color fillColorValue = shaderModule.fillSetting.getValue();

         selectedFillColor = capturedShaderForClusterAlpha.applyClusterAlpha(fillColorValue);
      }

      Color fillColor = selectedFillColor;
      capturedProgramForFillColor.set("u_FillColor", fillColor);
      ShaderUniforms capturedProgramForOutlineColor = this.uniforms;

      Color outlineColor = shaderModule.outlineColorSetting.getValue();
      capturedProgramForOutlineColor.set("u_OutlineColor", outlineColor);
      ShaderUniforms capturedProgramForDecoratorKind = this.uniforms;

      ShaderModule.TypeChoice decoratorTypeValue = shaderModule.typeSetting.getValue();
      int decoratorKind = decoratorTypeValue.ordinal();
      capturedProgramForDecoratorKind.set("u_Dots", decoratorKind);
      ShaderUniforms capturedProgramForDecoratorRadius = this.uniforms;

      Integer decoratorRadiusValue = shaderModule.radiusSetting.getValue();
      int decoratorRadius = decoratorRadiusValue;
      capturedProgramForDecoratorRadius.set("u_DotsRadius", decoratorRadius);
      ShaderUniforms capturedProgramForDecoratorAlpha = this.uniforms;

      Setting<Float> decoratorAlphaOption = shaderModule.alphaSetting;
      Float decoratorAlphaValue = decoratorAlphaOption.getValue();
      float decoratorAlpha = decoratorAlphaValue;
      capturedProgramForDecoratorAlpha.set("u_DotsAlpha", decoratorAlpha);
   }

   public SolidShaderPass() {
      super("base.vert", "solid.frag", DISTANCE_PASS);

      this.initializeProgram();
   }

}
