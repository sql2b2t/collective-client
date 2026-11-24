package me.collective.client.render;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.ESPModule;

public class ESPOutlineShaderPass extends PostProcessShaderPass {
   private static ESPModule espModule;
   private static ESPModule espModule() {
      if (espModule == null) {
         espModule = (ESPModule)ClientContext.getModule(ESPModule.class);
      }
      return espModule;
   }

   @Override
   public void updateUniforms() {
      super.updateUniforms();
      ESPModule espModule = espModule();
      if (espModule == null) {
         return;
      }
      ShaderUniforms widthUniforms = this.uniforms;

      widthUniforms.set("u_Width", 2);
      ShaderUniforms glowUniforms = this.uniforms;

      glowUniforms.set("u_GlowMultiplier", 1.0F);
      ShaderUniforms fillOpacityUniforms = this.uniforms;

      float fillOpacity = (espModule.blockFillOpacitySetting.getValue());
      fillOpacityUniforms.set("u_FillOpacity", fillOpacity);
   }

   public ESPOutlineShaderPass() {
      super("base.vert", "outline.frag");

      this.initializeProgram();
   }
}
