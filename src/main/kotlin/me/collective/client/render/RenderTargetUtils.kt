package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Deque
import me.collective.client.ClientAccess
import me.collective.mixin.MinecraftAccessor
import org.apache.commons.io.IOUtils

object RenderTargetUtils : ClientAccess {
    private data class TextureOverridePair(val color: GpuTextureView?, val depth: GpuTextureView?)

    private val savedMainRenderTargets: Deque<RenderTarget> = ArrayDeque()
    private val savedTextureOverrides: Deque<TextureOverridePair> = ArrayDeque()

    @JvmStatic
    fun popFramebuffer() {
        (ClientAccess.collectiveMinecraft.levelRenderer as LevelRendererFramebufferAccess).`collective$popFramebuffer`()
    }

    @JvmStatic
    fun restoreRenderTargets() {
        if (savedMainRenderTargets.isEmpty()) {
            return
        }

        popFramebuffer()
        setMainRenderTarget(savedMainRenderTargets.pop())
        if (!savedTextureOverrides.isEmpty()) {
            val prev = savedTextureOverrides.pop()
            RenderSystem.outputColorTextureOverride = prev.color
            RenderSystem.outputDepthTextureOverride = prev.depth
        }
    }

    @JvmStatic
    fun pushFramebuffer(target: RenderTarget?) {
        if (target == null) {
            return
        }
        (ClientAccess.collectiveMinecraft.levelRenderer as LevelRendererFramebufferAccess).`collective$pushFramebuffer`(target)
    }

    @JvmStatic
    fun overrideRenderTargets(target: RenderTarget?) {
        if (target == null) {
            return
        }
        savedMainRenderTargets.push(ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget())
        savedTextureOverrides.push(
            TextureOverridePair(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride)
        )
        pushFramebuffer(target)
        setMainRenderTarget(target)
        RenderSystem.outputColorTextureOverride = target.colorTextureView
        RenderSystem.outputDepthTextureOverride = if (target.useDepth) target.depthTextureView else null
    }

    @JvmStatic
    fun setMainRenderTarget(target: RenderTarget?) {
        if (target == null) {
            return
        }
        ClientAccess.collectiveMinecraft.gameRenderer.mainRenderTarget = target
    }

    @JvmStatic
    fun readShaderSource(resourcePath: String): String {
        val resourceLocation = "/assets/collective/shaders/$resourcePath"
        try {
            val stream = BaseShaderPass::class.java.getResourceAsStream(resourceLocation)
                ?: throw IllegalStateException("Missing shader resource: $resourceLocation")
            return stream.use { IOUtils.toString(it, StandardCharsets.UTF_8) }
        } catch (failure: IOException) {
            throw IllegalStateException("Failed to read shader resource: $resourceLocation", failure)
        }
    }
}
