package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.managers.RotationManager
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import java.awt.Color
import kotlin.math.max

class FadingBoxRenderState {
    var bounds: AABB? = null
    private var fadeStartMillis: Long = 0L
    private var lineWidth: Float = 1.0F

    fun refreshFade() {
        val currentTimeMillis = System.currentTimeMillis()
        this.fadeStartMillis = currentTimeMillis + 150L
    }

    fun setBounds(position: BlockPos) {
        this.bounds = AABB(position)
    }

    fun getFadeFactor(fadeDuration: Float): Float {
        val elapsedMillis = max(System.currentTimeMillis() - this.fadeStartMillis, 0L)
        if (fadeDuration <= 0.0F) {
            return if (elapsedMillis == 0L) 1.0F else 0.0F
        }
        return Mth.clamp(1.0F - elapsedMillis / fadeDuration, 0.0F, 1.0F)
    }

    fun setLineWidth(lineWidth: Float) {
        this.lineWidth = lineWidth
    }

    fun render(
        poseStack: PoseStack,
        fillColor: Color,
        outlineColor: Color,
        fadeDuration: Float,
        fadeEnabled: Boolean
    ) {
        val boundsSnapshot = this.bounds ?: return
        if (!RotationManager.isVisible(boundsSnapshot)) {
            return
        }

        val fadeFactor = this.getFadeFactor(fadeDuration)
        val renderFill = if (fadeEnabled) {
            ColorUtils.withAlpha(fillColor, (fillColor.alpha * fadeFactor).toInt())
        } else {
            fillColor
        }

        WorldBoxRenderer.fillBox(poseStack, boundsSnapshot, renderFill)

        val renderOutline = if (fadeEnabled) {
            ColorUtils.withAlpha(outlineColor, (outlineColor.alpha * fadeFactor).toInt())
        } else {
            outlineColor
        }

        WorldBoxRenderer.outlineBox(poseStack, boundsSnapshot, renderOutline, this.lineWidth)

        if (fadeFactor == 0.0F) {
            this.bounds = null
        }
    }
}
