package me.collective.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderDefines
import net.minecraft.client.renderer.ShaderProgram
import net.minecraft.resources.ResourceLocation

enum class FilterMode {
    NEAREST,
    LINEAR
}

object ClientRenderPipelines {
    enum class BlitPipeline(
        private val blendEnabled: Boolean,
        private val srcRgb: GlStateManager.SourceFactor,
        private val dstRgb: GlStateManager.DestFactor,
        private val srcAlpha: GlStateManager.SourceFactor,
        private val dstAlpha: GlStateManager.DestFactor
    ) {
        TRANSLUCENT(
            true,
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        ),
        LIGHTNING(
            true,
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE
        ),
        PREMULTIPLIED_ALPHA(
            true,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        ),
        NO_BLEND(
            false,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );

        fun applyBlendState() {
            if (blendEnabled) {
                RenderSystem.enableBlend()
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            } else {
                RenderSystem.disableBlend()
            }
        }
    }

    @JvmField
    val POSITION_TEX_COLOR: ShaderProgram = CoreShaders.POSITION_TEX_COLOR

    @JvmField
    val BLIT_TRANSLUCENT: BlitPipeline = BlitPipeline.TRANSLUCENT

    @JvmField
    val BLIT_LIGHTNING: BlitPipeline = BlitPipeline.LIGHTNING

    @JvmField
    val BLIT_PREMULTIPLIED_ALPHA: BlitPipeline = BlitPipeline.PREMULTIPLIED_ALPHA

    @JvmField
    val BLIT_NO_BLEND: BlitPipeline = BlitPipeline.NO_BLEND

    @JvmField
    val AMBIENCE: ShaderProgram = ShaderProgram(
        ResourceLocation.withDefaultNamespace("core/collective_lightmap"),
        DefaultVertexFormat.BLIT_SCREEN,
        ShaderDefines.EMPTY
    )

    @JvmField
    val OCCLUSION: ShaderProgram = ShaderProgram(
        ResourceLocation.withDefaultNamespace("core/collective_occlusion"),
        DefaultVertexFormat.BLIT_SCREEN,
        ShaderDefines.EMPTY
    )

    @JvmStatic
    fun isGlint(renderType: RenderType?): Boolean {
        if (renderType == null) return false
        return renderType == RenderType.glint() ||
            renderType == RenderType.glintTranslucent() ||
            renderType == RenderType.entityGlint() ||
            renderType == RenderType.armorEntityGlint() ||
            renderType == ClientRenderTypes.shineGlint() ||
            renderType.toString().contains("glint")
    }
}
