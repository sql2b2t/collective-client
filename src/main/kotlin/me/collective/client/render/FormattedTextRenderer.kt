package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import net.minecraft.network.chat.Style
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink

class FormattedTextRenderer(
    private val poses: PoseStack,
    private val baseColor: Int,
    brightness: Float,
    private val atlas: FontAtlas,
) : FormattedCharSink {
    private val brightness: Int = (brightness * 255.0f).toInt()
    private var cursorX = 0.0f
    private var cursorY = 0.0f

    override fun accept(index: Int, style: Style, codePoint: Int): Boolean {
        val textColor = style.color
        var color = if (textColor == null) {
            baseColor
        } else {
            ARGB.color(ARGB.alpha(baseColor), textColor.value)
        }
        if (brightness != 255) {
            color = ColorUtils.modulateRgb(color, brightness, brightness, brightness)
        }
        cursorX += atlas.drawGlyph(
            poses,
            AtlasTextRenderer.glyphMesh,
            Character.toString(codePoint)[0],
            cursorX,
            cursorY,
            color,
        )
        return true
    }

    internal fun draw(sequence: FormattedCharSequence?, x: Float, y: Float): Int {
        if (sequence == null) {
            return 0
        }
        cursorX = x * 4.0f
        cursorY = y * 4.0f
        poses.pushPose()
        try {
            poses.translate(FontRenderer.getTranslate().toFloat(), FontRenderer.getShift().toFloat(), 0.0f)
            poses.scale(0.25f, 0.25f, 1.0f)
            if (!AtlasTextRenderer.glyphMesh.isBuilding()) {
                AtlasTextRenderer.glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            }
            sequence.accept(this)
        } finally {
            poses.popPose()
        }
        return (cursorX / 4.0f).toInt()
    }
}
