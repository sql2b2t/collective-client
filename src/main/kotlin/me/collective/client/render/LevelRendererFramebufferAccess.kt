package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderTarget

interface LevelRendererFramebufferAccess {
    fun `collective$popFramebuffer`()

    fun `collective$pushFramebuffer`(target: RenderTarget)
}
