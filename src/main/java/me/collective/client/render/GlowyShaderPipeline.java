package me.collective.client.render;

import me.collective.client.ClientAccess;
import me.collective.client.managers.RenderTargetRegistry;
import com.mojang.blaze3d.pipeline.RenderTarget;

public final class GlowyShaderPipeline extends ShaderPipeline {
   private static final RenderTarget HORIZONTAL_TARGET = RenderTargetRegistry.createRenderTarget("collective_glow_horizontal", 2);
   private static final RenderTarget VERTICAL_TARGET = RenderTargetRegistry.createRenderTarget("collective_glow_vertical", 2);

   public GlowyShaderPipeline() {
      super(MaskShaderRenderer.MASK_TARGET);
      CompiledShaderProgram program = CompiledShaderProgram.fromResources("base.vert", "multi/glowy.frag");
      this.addStage(new GlowStage(program, VERTICAL_TARGET, 0, true));
      this.addStage(new GlowStage(program, HORIZONTAL_TARGET, 0, false));
      this.addStage(new GlowStage(program, VERTICAL_TARGET, 1, false));
      this.enableOutputBlending();
      this.enableLinearOutputFiltering();
   }

   private static final class GlowStage extends RenderTargetShaderStage {
      private final int pass;
      private final boolean downsample;

      private GlowStage(CompiledShaderProgram program, RenderTarget outputTarget, int pass, boolean downsample) {
         super(program, outputTarget);
         this.pass = pass;
         this.downsample = downsample;
      }

      @Override
      public void updateUniforms() {
         super.updateUniforms();
         int width = (int)Math.ceil(MaskShaderRenderer.getBlurWidth() * ClientAccess.collectiveMinecraft.getWindow().getGuiScale() * 2.0F / 2.0F);
         this.uniforms.set("u_Pass", this.pass);
         this.uniforms.set("u_Width", this.downsample ? 0 : width);
         this.uniforms.set("u_Sigma", width / 3.0F);
         this.uniforms.set("u_Bloom", MaskShaderRenderer.getBloom());
         this.uniforms.set("u_Opacity", MaskShaderRenderer.getOpacity());
      }
   }
}
