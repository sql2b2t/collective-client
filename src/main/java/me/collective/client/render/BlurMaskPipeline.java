package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;

public class BlurMaskPipeline extends ShaderPipeline {

   public BlurMaskPipeline() {
      super(ClientAccess.collectiveMinecraft.getMainRenderTarget());

      CompiledShaderProgram program = CompiledShaderProgram.fromResources("base.vert", "multi/blur_mask.frag");
      ShaderStage verticalStage = new BlurMaskPipeline.DirectionalStage(
         program, false, MaskShaderRenderer.VERTICAL_TARGET
      );

      this.addStage(verticalStage);
      ShaderStage horizontalStage = new BlurMaskPipeline.DirectionalStage(
         program, true, MaskShaderRenderer.HORIZONTAL_TARGET
      );

      this.addStage(horizontalStage);
   }

   public static class DirectionalStage extends RenderTargetShaderStage {
      private final int directionX;
      private final int directionY;

      @Override
      public void updateUniforms() {
         super.updateUniforms();
         ShaderUniforms maskUniforms = this.uniforms;
         RenderTarget maskTarget = MaskShaderRenderer.MASK_TARGET;
         maskUniforms.set("u_Mask", 16, maskTarget);
         ShaderUniforms widthUniforms = this.uniforms;
         float blurWidth = MaskShaderRenderer.getBlurWidth();
         widthUniforms.set("u_Width", blurWidth);
         ShaderUniforms sigmaUniforms = this.uniforms;
         float widthForSigma = MaskShaderRenderer.getBlurWidth();
         float sigma = 1.0F + widthForSigma / 1.5F;
         sigmaUniforms.set("u_Sigma", sigma);
         ShaderUniforms directionUniforms = this.uniforms;
         float xDirection = this.directionX;
         float yDirection = this.directionY;
         directionUniforms.set("u_Direction", xDirection, yDirection);
      }

      public DirectionalStage(CompiledShaderProgram program, boolean horizontal, RenderTarget outputTarget) {
         super(program, outputTarget);
         this.directionX = horizontal ? 1 : 0;
         this.directionY = horizontal ? 0 : 1;
      }

   }
}
