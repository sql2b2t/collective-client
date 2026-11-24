package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.ArrayList;
import java.util.List;

public class ShaderPipeline implements ClientAccess, ShaderPass {
   protected final List<ShaderStage> stages = new ArrayList<>();
   protected final RenderTarget sourceTarget;
   private boolean blendOutput;
   private FilterMode outputFilter = FilterMode.NEAREST;

   @Override
   public CompiledShaderProgram getProgram() {
      if (this.stages.isEmpty()) {
         return null;
      }
      ShaderStage firstStage = this.stages.getFirst();
      return firstStage.getProgram();
   }

   @Override
   public void updateUniforms() {
   }

   protected void enableOutputBlending() {
      this.blendOutput = true;
   }

   protected void enableLinearOutputFiltering() {
      this.outputFilter = FilterMode.LINEAR;
   }

   public ShaderPipeline(RenderTarget sourceTarget) {
      this.sourceTarget = sourceTarget;
   }

   public void addStage(ShaderStage stage) {
      this.stages.add(stage);
   }

   @Override
   public void render(Runnable ignoredDraw) {
      if (!this.stages.isEmpty()) {
         RenderTarget currentTarget = this.sourceTarget;

         for (ShaderStage stage : this.stages) {
            stage.apply(currentTarget);
            currentTarget = stage.getOutputTarget();
         }

         RenderUtils.blitToMain(
            currentTarget,
            this.blendOutput
               ? ClientRenderPipelines.BLIT_TRANSLUCENT
               : ClientRenderPipelines.BLIT_NO_BLEND,
            this.outputFilter
         );
      }
   }

}
