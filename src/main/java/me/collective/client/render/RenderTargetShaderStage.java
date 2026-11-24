package me.collective.client.render;

import me.collective.client.managers.RenderStateManager;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

public class RenderTargetShaderStage implements ClientAccess, ShaderStage {
   protected final CompiledShaderProgram program;
   protected final ShaderUniforms uniforms;
   protected final RenderTarget outputTarget;

   @Override
   public CompiledShaderProgram getProgram() {
      return this.program;
   }

   @Override
   public void updateUniforms() {
      this.uniforms.set("u_Proj", RenderUtils.WORLD_PROJECTION_MATRIX);
      ShaderUniforms modelViewUniforms = this.uniforms;
      Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
      modelViewUniforms.set("u_ModelView", modelViewMatrix);
      ShaderUniforms sizeUniforms = this.uniforms;

      sizeUniforms.set("u_Size", (float)this.outputTarget.width, (float)this.outputTarget.height);
      this.uniforms.set("u_Texture", 15);
      this.uniforms.set("u_Time", (float)GLFW.glfwGetTime());
   }

   @Override
   public RenderTarget getOutputTarget() {
      return this.outputTarget;
   }

   protected void drawFullscreenQuad() {
      Tesselator capturedTesselator = Tesselator.getInstance();
      VertexFormat vertexFormat = DefaultVertexFormat.POSITION;
      Mode quadMode = Mode.QUADS;

      BufferBuilder vertices = capturedTesselator.begin(quadMode, vertexFormat);

      vertices.addVertex(-1.0F, -1.0F, 0.0F);

      vertices.addVertex(-1.0F, 1.0F, 0.0F);

      vertices.addVertex(1.0F, 1.0F, 0.0F);

      vertices.addVertex(1.0F, -1.0F, 0.0F);
      RenderType renderType = ClientRenderTypes.entityShader();
      MeshData mesh = vertices.buildOrThrow();

      renderType.draw(mesh);
   }

   public RenderTargetShaderStage(String vertexResource, String fragmentResource, RenderTarget outputTarget) {
      this(
         CompiledShaderProgram.compile(
            RenderTargetUtils.readShaderSource(vertexResource),
            RenderTargetUtils.readShaderSource(fragmentResource)
         ),
         outputTarget
      );
   }

   public RenderTargetShaderStage(CompiledShaderProgram program, RenderTarget outputTarget) {
      this.program = program;
      this.uniforms = new ShaderUniforms(this.program.programId());
      this.outputTarget = outputTarget;
   }

   @Override
   public void apply(RenderTarget inputTarget) {
      RenderUtils.clearColor(
         this.outputTarget
      );
      RenderUtils.clearDepth(
         this.outputTarget
      );
      RenderTargetUtils.overrideRenderTargets(
         this.outputTarget
      );
      try {
         int inputTextureId = ((GlTexture)inputTarget.getColorTexture()).glId();
         ShaderStageRenderState state = ShaderStageRenderState.INSTANCE;
         state.configure(this, inputTextureId);
         RenderStateManager.runWithStates(
            this::drawFullscreenQuad,
            ShaderStageRenderState.INSTANCE
         );
      } finally {
         RenderTargetUtils.restoreRenderTargets();
      }
   }
}
