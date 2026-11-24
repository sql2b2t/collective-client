package me.collective.client.render;

import me.collective.client.managers.RenderStateManager;
import me.collective.client.managers.RenderTargetRegistry;
import me.collective.client.managers.ShaderRegistry;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

public class PostProcessShaderPass extends BaseShaderPass {
   private static final Identifier OVERLAY_TEXTURE;
   public static final RenderTarget POST_TARGET;
   protected static int overlayTextureId;

   @Override
   public void updateUniforms() {
      ShaderUniforms projectionUniforms = this.uniforms;
      Matrix4f projectionMatrix = RenderUtils.WORLD_PROJECTION_MATRIX;
      projectionUniforms.set("u_Proj", projectionMatrix);
      ShaderUniforms modelViewUniforms = this.uniforms;
      Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
      modelViewUniforms.set("u_ModelView", modelViewMatrix);
      ShaderUniforms sizeUniforms = this.uniforms;
      float capturedWidth = (ClientAccess.collectiveMinecraft.getWindow()).getWidth();
      float height = (ClientAccess.collectiveMinecraft.getWindow()).getHeight();
      sizeUniforms.set("u_Size", capturedWidth, height);
      this.uniforms.set("u_Texture", 15);
      this.uniforms.set("u_Time", (float)GLFW.glfwGetTime());
   }

   public PostProcessShaderPass(String vertexResource, String fragmentResource) {
      super(vertexResource, fragmentResource);
   }

   static {
      String overlayPath = "textures/overlay.png";
      String overlayNamespace = "collective-mount";
      OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath(overlayNamespace, overlayPath);
      POST_TARGET = RenderTargetRegistry.createRenderTarget("post");
      overlayTextureId = -1;

   }

   @Override
   public void render(Runnable draw) {
      TextureManager capturedTextureManager = ClientAccess.collectiveMinecraft.getTextureManager();
      Identifier overlayResource = OVERLAY_TEXTURE;
      AbstractTexture overlayTexture = capturedTextureManager.getTexture(overlayResource);
      GlTexture overlayGlTexture = (GlTexture)overlayTexture.getTexture();
      overlayTextureId = overlayGlTexture.glId();
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      RenderUtils.clearColor(
         POST_TARGET
      );
      RenderUtils.clearDepth(
         POST_TARGET
      );

      this.renderToTarget(draw);
      if (buffers.hasColor()) {
         buffers.reset();
         this.prepareOutput();
         GlTexture postGlTexture = (GlTexture)POST_TARGET.getColorTexture();
         int postTextureId = postGlTexture.glId();
         ShaderPassRenderState renderState = ShaderPassRenderState.INSTANCE;
         renderState.configure(this, postTextureId);
         RenderStateManager.addStates(
            ShaderPassRenderState.INSTANCE
         );

         PostProcessShaderPass passForQuad = this;
         try {
            passForQuad.drawFullscreenQuad();
         } finally {
            RenderStateManager.reset();
         }
      }
   }

   protected void prepareOutput() {
   }

   protected void renderToTarget(Runnable draw) {
      ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
      RenderTargetUtils.overrideRenderTargets(
         POST_TARGET
      );
      try {
         ClientContext.shaderRegistry.renderingIntoTarget = true;
         draw.run();
         buffers.endBatchAndClean();
      } finally {
         ClientContext.shaderRegistry.renderingIntoTarget = false;
         RenderTargetUtils.restoreRenderTargets();
      }
   }

   private void drawFullscreenQuad() {
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

}
