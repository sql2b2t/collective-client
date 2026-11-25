package me.collective.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap
import it.unimi.dsi.fastutil.chars.CharArraySet
import java.awt.Font
import kotlin.math.max
import me.collective.client.ClientContext
import me.collective.client.modules.client.FontsModule
import me.collective.client.settings.options.FontsStyleChoice
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.FormattedCharSequence

class AtlasTextRenderer(private val atlas: FontAtlas) {
    private val splitter = AtlasStringSplitter(this)
    private var cursorX = 0.0f
    private var cursorY = 0.0f

    fun flush() {
        if (glyphMesh.isBuilding()) {
            val fonts = fonts()
            val antiAlias = fonts != null && fonts.antiAliasSetting.value
            MeshDrawContext.setRenderType(ClientRenderTypes.font(FontAtlas.ATLAS_TEXTURE, antiAlias))
            MeshBuffer.draw(glyphMesh)
        }
        finishBuffers()
    }

    fun getBufferSource(): MultiBufferSource = bufferSource

    fun getSplitter(): AtlasStringSplitter = splitter

    fun drawCentered(poses: PoseStack, text: String?, x: Double, y: Double, argb: Int): Int {
        return draw(poses, text, x.toFloat() - getWidth(text) / 2.0f, y.toFloat(), argb, true)
    }

    fun getHeight(): Int = (atlas.getHeight() * ATLAS_SCALE).toInt()

    fun draw(poses: PoseStack, text: String?, x: Float, y: Float, argb: Int): Int {
        return draw(poses, text, x, y, argb, true)
    }

    fun draw(poses: PoseStack, text: String?, x: Double, y: Double, argb: Int): Int {
        return draw(poses, text, x.toFloat(), y.toFloat(), argb, true)
    }

    fun draw(poses: PoseStack, sequence: FormattedCharSequence?, x: Float, y: Float, argb: Int, shadow: Boolean): Int {
        var endX: Int
        if (shadow) {
            endX = FormattedTextRenderer(poses, argb, 0.25f, atlas)
                .draw(sequence, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset())
            endX = max(endX, FormattedTextRenderer(poses, argb, 1.0f, atlas).draw(sequence, x, y))
        } else {
            endX = FormattedTextRenderer(poses, argb, 1.0f, atlas).draw(sequence, x, y)
        }
        return endX
    }

    fun draw(poses: PoseStack, text: String?, x: Float, y: Float, argb: Int, shadow: Boolean): Int {
        if (!shadow) {
            return drawPass(poses, text, x, y, argb, false)
        }
        val shadowEnd = drawPass(
            poses,
            text,
            x + FontRenderer.getShadowOffset(),
            y + FontRenderer.getShadowOffset(),
            argb,
            true,
        )
        return max(shadowEnd, drawPass(poses, text, x, y, argb, false))
    }

    private fun renderString(poses: PoseStack, text: String, baseColor: Int) {
        var color = baseColor
        poses.pushPose()
        try {
            poses.translate(FontRenderer.getTranslate().toFloat(), FontRenderer.getShift().toFloat(), 0.0f)
            poses.scale(ATLAS_SCALE, ATLAS_SCALE, 1.0f)
            if (!glyphMesh.isBuilding()) {
                glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            }
            var formattingCode = false
            for (character in text.toCharArray()) {
                if (formattingCode) {
                    formattingCode = false
                    val format = FORMATTING_CODES[character.lowercaseChar()]
                    if (format == null || format == ChatFormatting.RESET) {
                        color = baseColor
                    } else if (format.color != null) {
                        color = format.color!!
                    }
                } else if (character == '\u00a7') {
                    formattingCode = true
                } else {
                    cursorX += atlas.drawGlyph(poses, glyphMesh, character, cursorX, cursorY, color)
                }
            }
        } finally {
            poses.popPose()
        }
    }

    fun getCharacterWidth(character: Char): Float {
        return atlas.getGlyphWidth(character) * ATLAS_SCALE
    }

    private fun drawPass(poses: PoseStack, text: String?, x: Float, y: Float, argb: Int, shadowPass: Boolean): Int {
        if (text == null) {
            return 0
        }
        var color = argb
        if (color ushr 24 == 0) {
            color = color or 0xFF000000.toInt()
        }
        if (shadowPass) {
            val alpha = color and 0xFF000000.toInt()
            color = alpha or ((color and 0x00FCFCFC) ushr 2)
        }
        cursorX = x / ATLAS_SCALE
        cursorY = y / ATLAS_SCALE
        renderString(poses, text, color)
        return (cursorX * ATLAS_SCALE).toInt()
    }

    fun drawWithoutShadow(poses: PoseStack, text: String?, x: Float, y: Float, argb: Int): Int {
        return draw(poses, text, x, y, argb, false)
    }

    fun drawWithoutShadow(poses: PoseStack, text: String?, x: Double, y: Double, argb: Int): Int {
        return draw(poses, text, x.toFloat(), y.toFloat(), argb, false)
    }

    fun getWidth(text: String?): Int {
        if (text == null) {
            return 0
        }
        var width = 0.0f
        var formattingCode = false
        for (character in text.toCharArray()) {
            if (formattingCode) {
                formattingCode = false
            } else if (character == '\u00a7') {
                formattingCode = true
            } else {
                width += atlas.getGlyphWidth(character)
            }
        }
        return (width * ATLAS_SCALE + 1.0f).toInt()
    }

    fun drawCenteredWithoutShadow(poses: PoseStack, text: String?, x: Double, y: Double, argb: Int): Int {
        return draw(poses, text, x.toFloat() - getWidth(text) / 2.0f, y.toFloat(), argb, false)
    }

    companion object {
        private const val ATLAS_SCALE = 0.25f
        private val FORMATTING_CODES: MutableMap<Char, ChatFormatting> = Char2ObjectOpenHashMap()
        private var fonts: FontsModule? = null

        @JvmField
        internal val glyphMesh: MeshBuffer = MeshBuffer.create()

        private val byteBuffer = ByteBufferBuilder(2048)
        private val immediateBuffers: BufferSource = MultiBufferSource.immediate(byteBuffer)
        private val bufferSource = MultiBufferSource(::getBuffer)
        private var lastRenderType: RenderType? = null

        init {
            for (format in ChatFormatting.entries) {
                FORMATTING_CODES[format.char] = format
            }
        }

        private fun fonts(): FontsModule? {
            if (fonts == null) {
                fonts = ClientContext.getModule(FontsModule::class.java)
            }
            return fonts
        }

        @JvmStatic
        fun finishBuffers() {
            immediateBuffers.endBatch()
            lastRenderType = null
        }

        @JvmStatic
        fun create(
            fontName: String,
            fontSize: Int,
            style: FontsStyleChoice,
            languages: Set<FontLanguage>,
        ): AtlasTextRenderer {
            val characters = CharArraySet(estimateCharacterCount(languages))
            for (language in languages) {
                language.getRange().addTo(characters)
            }
            val awtStyle = when (style) {
                FontsStyleChoice.PLAIN -> Font.PLAIN
                FontsStyleChoice.BOLD -> Font.BOLD
                FontsStyleChoice.ITALIC -> Font.ITALIC
                FontsStyleChoice.BOLDITALIC -> Font.BOLD or Font.ITALIC
            }
            val atlas = FontAtlas(Font(fontName, awtStyle, fontSize))
            atlas.buildAtlas(characters)
            atlas.uploadTexture()
            return AtlasTextRenderer(atlas)
        }

        private fun estimateCharacterCount(languages: Collection<FontLanguage>): Int {
            var count = 512
            if (languages.contains(FontLanguage.CHINESE)) {
                count += 2500
            }
            if (languages.contains(FontLanguage.JAPANESE)) {
                count += 3000
            }
            return count
        }

        private fun getBuffer(renderType: RenderType): VertexConsumer {
            if (lastRenderType != null && lastRenderType !== renderType) {
                GuiDrawUtils.flush()
            }
            lastRenderType = renderType
            return immediateBuffers.getBuffer(renderType)
        }
    }
}
