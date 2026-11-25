package me.collective.client.render

import java.awt.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth

object LoadingOverlayRenderer {
    const val BACKGROUND_RGB: Int = 1644825

    private val LOGO_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        "collective", "textures/collective.png"
    )

    @JvmStatic
    @JvmOverloads
    fun render(
        graphics: GuiGraphics,
        opacity: Float,
        backgroundOpacity: Float,
        logoScale: Float = 0.5f
    ) {
        val clampedOpacity = Mth.clamp(opacity, 0.0f, 1.0f)
        val logoX = ((GuiScaleUtils.getScaledWidth() - 256.0f * logoScale) / 2.0f).toInt()
        val logoY = ((GuiScaleUtils.getScaledHeight() - 256.0f * logoScale) / 2.0f).toInt()
        val backgroundArgb = ColorUtils.toArgb(
            Color(BACKGROUND_RGB, false),
            backgroundOpacity * clampedOpacity
        )
        val logoArgb = ColorUtils.toArgb(Color.white, clampedOpacity)
        graphics.fill(
            0,
            0,
            GuiScaleUtils.getScaledWidth(),
            GuiScaleUtils.getScaledHeight(),
            backgroundArgb
        )
        graphics.pose().pushPose()
        graphics.pose().translate(logoX.toFloat(), logoY.toFloat(), 0.0f)
        graphics.pose().scale(logoScale, logoScale, 1.0f)
        graphics.blit(
            RenderType::guiTextured,
            LOGO_TEXTURE,
            0,
            0,
            0.0f,
            0.0f,
            256,
            256,
            256,
            256,
            logoArgb
        )
        graphics.pose().popPose()
    }
}
