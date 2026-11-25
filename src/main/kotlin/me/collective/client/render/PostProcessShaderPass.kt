package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.RenderStateManager
import me.collective.client.managers.RenderTargetRegistry
import me.collective.client.managers.ShaderRegistry
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW

open class PostProcessShaderPass(
   vertexResource: String,
   fragmentResource: String
) : BaseShaderPass(vertexResource, fragmentResource) {

   override fun updateUniforms() {
      this.uniforms.set("u_Proj", RenderUtils.WORLD_PROJECTION_MATRIX)
      this.uniforms.set("u_ModelView", RenderSystem.getModelViewStack())
      val window = ClientAccess.collectiveMinecraft.window
      this.uniforms.set("u_Size", window.width.toFloat(), window.height.toFloat())
      this.uniforms.set("u_Texture", 15)
      this.uniforms.set("u_Time", GLFW.glfwGetTime().toFloat())
   }

   override fun render(draw: Runnable) {
      val textureManager = ClientAccess.collectiveMinecraft.textureManager
      val overlayTexture = textureManager.getTexture(OVERLAY_TEXTURE)
      overlayTextureId = overlayTexture.id
      val buffers = ShaderRegistry.getBufferSource()
      RenderUtils.clearColor(POST_TARGET)
      RenderUtils.clearDepth(POST_TARGET)

      this.renderToTarget(draw)
      if (buffers.hasColor()) {
         buffers.reset()
         this.prepareOutput()
         val postTextureId = POST_TARGET.colorTextureId
         ShaderPassRenderState.configure(this, postTextureId)
         RenderStateManager.addStates(ShaderPassRenderState)
         try {
            this.drawFullscreenQuad()
         } finally {
            RenderStateManager.reset()
         }
      }
   }

   protected open fun prepareOutput() {
   }

   protected open fun renderToTarget(draw: Runnable) {
      val buffers = ShaderRegistry.getBufferSource()
      RenderTargetUtils.overrideRenderTargets(POST_TARGET)
      try {
         ClientContext.shaderRegistry.renderingIntoTarget = true
         draw.run()
         buffers.endBatchAndClean()
      } finally {
         ClientContext.shaderRegistry.renderingIntoTarget = false
         RenderTargetUtils.restoreRenderTargets()
      }
   }

   private fun drawFullscreenQuad() {
      val vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
      vertices.addVertex(-1.0f, -1.0f, 0.0f)
      vertices.addVertex(-1.0f, 1.0f, 0.0f)
      vertices.addVertex(1.0f, 1.0f, 0.0f)
      vertices.addVertex(1.0f, -1.0f, 0.0f)
      val renderType = ClientRenderTypes.entityShader()
      val mesh = vertices.buildOrThrow()
      renderType.draw(mesh)
   }

   companion object {
      private val OVERLAY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("collective-mount", "textures/overlay.png")

      @JvmField
      val POST_TARGET: RenderTarget = RenderTargetRegistry.createRenderTarget("post")

      @JvmField
      protected var overlayTextureId: Int = -1
   }
}
