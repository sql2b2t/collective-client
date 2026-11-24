package me.collective.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface ShaderStage {
   CompiledShaderProgram getProgram();

   void updateUniforms();

   RenderTarget getOutputTarget();

   void apply(RenderTarget inputTarget);
}
