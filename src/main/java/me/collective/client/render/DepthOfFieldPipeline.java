package me.collective.client.render;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.settings.Setting;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class DepthOfFieldPipeline extends ShaderPipeline {
   private static AmbienceModule ambience;
   private static AmbienceModule ambience() {
      if (ambience == null) {
         ambience = ClientContext.getModule(AmbienceModule.class);
      }
      return ambience;
   }

   public DepthOfFieldPipeline() {
      super(ClientAccess.collectiveMinecraft.getMainRenderTarget());

      CompiledShaderProgram program = CompiledShaderProgram.fromResources(
         "base.vert", "multi/dof.frag"
      );
      ShaderStage verticalStage = new DepthOfFieldPipeline.DirectionalStage(
         program, false, MaskShaderRenderer.VERTICAL_TARGET
      );

      this.addStage(verticalStage);
      ShaderStage horizontalStage = new DepthOfFieldPipeline.DirectionalStage(
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

         GL13.glActiveTexture(GL13.GL_TEXTURE14);
         AmbienceModule capturedAmbience = DepthOfFieldPipeline.ambience();
         if (capturedAmbience == null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            return;
         }

         RenderTarget depthTarget = capturedAmbience.getDepthRenderTarget();
         if (depthTarget == null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            return;
         }

         GpuTexture depthTexture = depthTarget.getDepthTexture();
         GlTexture glTexture = (GlTexture)depthTexture;
         int depthTextureId = glTexture.glId();

         GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId);
         ShaderUniforms capturedDepthUniforms = this.uniforms;

         capturedDepthUniforms.set("DepthSampler", 14);
         ShaderUniforms capturedFocusUniforms = this.uniforms;

         float focusDistance = 0.998F;

         capturedFocusUniforms.set("FocusDistance", focusDistance);
         ShaderUniforms capturedStrengthUniforms = this.uniforms;

         Setting<Float> strengthOption = capturedAmbience.strengthSetting;
         Float strengthValue = strengthOption.getValue();
         float rawStrength = strengthValue;

         float blurStrength = rawStrength * 10.0F;

         capturedStrengthUniforms.set("BlurStrength", blurStrength);
         ShaderUniforms capturedDirectionUniforms = this.uniforms;

         float capturedXDirection = this.directionX;
         float yDirection = this.directionY;

         capturedDirectionUniforms.set("u_Direction", capturedXDirection, yDirection);
         GL13.glActiveTexture(GL13.GL_TEXTURE0);
      }

      public DirectionalStage(CompiledShaderProgram program, boolean horizontal, RenderTarget outputTarget) {
         super(program, outputTarget);
         this.directionX = horizontal ? 1 : 0;
         this.directionY = horizontal ? 0 : 1;
      }

   }
}
