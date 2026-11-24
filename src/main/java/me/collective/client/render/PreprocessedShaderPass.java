package me.collective.client.render;

import me.collective.client.ClientContext;
import me.collective.client.managers.RenderTargetRegistry;
import me.collective.client.modules.render.ShaderModule;
import com.mojang.blaze3d.pipeline.RenderTarget;

public abstract class PreprocessedShaderPass extends PostProcessShaderPass {
   private static ShaderModule shaderModule;
   protected static ShaderModule shaderModule() {
      if (shaderModule == null) {
         shaderModule = ClientContext.getModule(ShaderModule.class);
      }
      return shaderModule;
   }
   private static final RenderTarget PRE_TARGET = RenderTargetRegistry.createRenderTarget("collective_pre_pass");
   protected static final PrePass COVERAGE_PASS = new PrePass("multi/coverage.frag", "u_Coverage", true);
   protected static final PrePass DISTANCE_PASS = new PrePass("multi/distance.frag", "u_Distance", false);
   private final PrePass prePass;

   protected PreprocessedShaderPass(String vertexResource, String fragmentResource, PrePass prePass) {
      super(vertexResource, fragmentResource);
      this.prePass = prePass;
   }

   @Override
   protected void prepareOutput() {
      ShaderModule shaderModule = shaderModule();
      if (shaderModule == null) {
         return;
      }
      if (!shaderModule.shouldSkipHandOutline() && shaderModule.lineWidthSetting.getValue() > 0 && this.prePass != null) {
         this.prePass.apply(POST_TARGET);
      }
   }

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      if (this.prePass != null) {
         this.uniforms.set(this.prePass.samplerName, 16, PRE_TARGET);
      }
   }

   protected static final class PrePass extends RenderTargetShaderStage {
      private final String samplerName;
      private final boolean coverage;

      private PrePass(String fragmentResource, String samplerName, boolean coverage) {
         super("base.vert", fragmentResource, PRE_TARGET);
         this.samplerName = samplerName;
         this.coverage = coverage;
      }

      @Override
      public void updateUniforms() {
         super.updateUniforms();
         ShaderModule shaderModule = shaderModule();
         if (shaderModule == null) {
            return;
         }
         this.uniforms.set("u_Width", shaderModule.lineWidthSetting.getValue());
         if (this.coverage) {
            this.uniforms.set("u_Step", shaderModule.qualitySetting.getValue());
         }
      }
   }
}
