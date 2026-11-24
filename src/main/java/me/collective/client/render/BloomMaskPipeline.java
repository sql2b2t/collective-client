package me.collective.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public class BloomMaskPipeline extends ShaderPipeline {

   public BloomMaskPipeline() {
      super(MaskShaderRenderer.MASK_TARGET);

      CompiledShaderProgram program = CompiledShaderProgram.fromResources("base.vert", "multi/bloom_mask.frag");
      ShaderStage horizontalStage = new BloomMaskPipeline.DirectionalStage(
         program, true, MaskShaderRenderer.HORIZONTAL_TARGET
      );

      this.addStage(horizontalStage);
      ShaderStage verticalStage = new BloomMaskPipeline.DirectionalStage(
         program, false, MaskShaderRenderer.VERTICAL_TARGET
      );

      this.addStage(verticalStage);

      this.enableOutputBlending();
   }

   public static class DirectionalStage extends RenderTargetShaderStage {
      private final int directionX;
      private final int directionY;

      @Override
      public void updateUniforms() {
         super.updateUniforms();
         ShaderUniforms capturedUniforms = this.uniforms;

         float blurWidth = MaskShaderRenderer.getBlurWidth();

         capturedUniforms.set("u_Width", blurWidth);
         capturedUniforms = this.uniforms;

         float widthForSigma = MaskShaderRenderer.getBlurWidth();

         float sigma = widthForSigma * 0.5F;

         capturedUniforms.set("u_Sigma", sigma);
         capturedUniforms = this.uniforms;

         float modulate = MaskShaderRenderer.getModulate();

         capturedUniforms.set("u_Modulate", modulate);
         capturedUniforms = this.uniforms;

         float bloom = MaskShaderRenderer.getBloom();

         capturedUniforms.set("u_Bloom", bloom);
         capturedUniforms = this.uniforms;

         float opacity = MaskShaderRenderer.getOpacity();

         capturedUniforms.set("u_Opacity", opacity);
         capturedUniforms = this.uniforms;

         float xDirection = this.directionX;
         float yDirection = this.directionY;

         capturedUniforms.set("u_Direction", xDirection, yDirection);
      }

      public DirectionalStage(CompiledShaderProgram program, boolean horizontal, RenderTarget outputTarget) {
         super(program, outputTarget);
         this.directionX = horizontal ? 1 : 0;
         this.directionY = horizontal ? 0 : 1;
      }

   }
}
