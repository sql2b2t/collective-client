package me.collective.client.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import me.collective.client.ClientAccess
import net.minecraft.resources.Identifier
import org.lwjgl.BufferUtils

class FontAtlas(private val font: Font) {
    private val glyphs = Char2ObjectOpenHashMap<Glyph>()
    private var fontHeight = -1
    private var atlasSize = -1

    fun release() {
        glyphs.clear()
        ClientAccess.collectiveMinecraft.textureManager.release(ATLAS_TEXTURE)
    }

    fun buildAtlas(characters: Collection<Char>) {
        atlasSize = align(calculateAtlasSize(characters), 2)
        val metricsImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = metricsImage.createGraphics()
        try {
            graphics.font = font
            configureGraphics(graphics)
            val metrics = graphics.fontMetrics
            var rowHeight = 0
            var cursorX = 0
            var cursorY = 0
            for (character in characters) {
                val bounds = metrics.getStringBounds(character.toString(), graphics)
                val width = align(bounds.bounds.width + 8, 2)
                val height = align(bounds.bounds.height, 2)
                if (cursorX + width >= atlasSize) {
                    cursorX = 0
                    cursorY += rowHeight + 2
                    rowHeight = 0
                }
                val minUv = Uv(cursorX.toFloat() / atlasSize, cursorY.toFloat() / atlasSize)
                val maxUv = Uv((cursorX + width).toFloat() / atlasSize, (cursorY + height).toFloat() / atlasSize)
                glyphs.put(character, Glyph(cursorX, cursorY, width, height, minUv, maxUv))
                fontHeight = max(fontHeight, height)
                rowHeight = max(rowHeight, height)
                cursorX += width
            }
            if ("Dialog" == font.family) {
                fontHeight -= 2
            }
        } finally {
            graphics.dispose()
            metricsImage.flush()
        }
    }

    private fun calculateAtlasSize(characters: Collection<Char>): Int {
        var maxWidth = -1.0
        var maxHeight = -1.0
        val context = FontRenderContext(AffineTransform(), true, true)
        for (character in characters) {
            val bounds = font.getStringBounds(character.toString(), context)
            maxWidth = max(maxWidth, bounds.width)
            maxHeight = max(maxHeight, bounds.height)
        }
        maxWidth += 8.0
        maxHeight += 4.0
        val width = ceil(sqrt(maxWidth * maxWidth * characters.size) / maxWidth)
        val height = ceil(sqrt(maxHeight * maxHeight * characters.size) / maxHeight)
        return ceil(max(width, height) * max(maxWidth, maxHeight)).toInt() + 1
    }

    private fun createMipLevel(mipLevel: Int): BufferedImage {
        val scale = 1 shl mipLevel
        val size = max(1, atlasSize / scale)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            val mipFont = if (mipLevel == 0) font else font.deriveFont(font.size2D / scale)
            graphics.font = mipFont
            graphics.color = Color.WHITE
            configureGraphics(graphics)
            val metrics = graphics.fontMetrics
            val padding = max(1, 2 / scale)
            val characters = glyphs.keys.iterator()
            while (characters.hasNext()) {
                val character = characters.nextChar()
                val glyph = glyphs.get(character)
                graphics.drawString(character.toString(), glyph.x / scale + padding, glyph.y / scale + metrics.ascent)
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

    fun uploadTexture() {
        val mipImages = arrayOfNulls<NativeImage>(2)
        try {
            for (level in mipImages.indices) {
                mipImages[level] = toNativeImage(createMipLevel(level))
            }
            @Suppress("UNCHECKED_CAST")
            ClientAccess.collectiveMinecraft.textureManager.register(
                ATLAS_TEXTURE,
                MipmapFontTexture({ ATLAS_LABEL }, mipImages as Array<NativeImage>),
            )
        } catch (exception: Exception) {
            for (image in mipImages) {
                image?.close()
            }
            throw RuntimeException(exception)
        }
    }

    fun drawGlyph(
        poses: PoseStack,
        consumer: VertexConsumer,
        character: Char,
        x: Float,
        y: Float,
        argb: Int,
    ): Float {
        val glyph = glyphs.get(character) ?: return 0.0f
        val z = FontRenderer.getZOffset().toFloat()
        consumer.addVertex(poses.last(), x, y + glyph.height, z).setColor(argb).setUv(glyph.minimumUv.u, glyph.maximumUv.v)
        consumer.addVertex(poses.last(), x + glyph.width, y + glyph.height, z).setColor(argb).setUv(glyph.maximumUv.u, glyph.maximumUv.v)
        consumer.addVertex(poses.last(), x + glyph.width, y, z).setColor(argb).setUv(glyph.maximumUv.u, glyph.minimumUv.v)
        consumer.addVertex(poses.last(), x, y, z).setColor(argb).setUv(glyph.minimumUv.u, glyph.minimumUv.v)
        return glyph.width - 8.0f
    }

    fun getGlyphWidth(character: Char): Float {
        val glyph = glyphs.get(character) ?: glyphs.get('?') ?: return 0.0f
        return glyph.width - 8.0f
    }

    fun getHeight(): Int = fontHeight

    private data class Uv(val u: Float, val v: Float)

    private data class Glyph(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val minimumUv: Uv,
        val maximumUv: Uv,
    )

    companion object {
        @JvmField
        val ATLAS_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("collective", "textures/font.png")

        @JvmField
        val ATLAS_LABEL: String = ATLAS_TEXTURE.toLanguageKey()

        const val LAYOUT_VALUE = 4
        const val SECONDARY_LAYOUT_VALUE = 1
        const val LAYOUT_SCALE = 0.25f

        private fun configureGraphics(graphics: Graphics2D) {
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }

        private fun align(value: Int, alignment: Int): Int {
            return (value + alignment - 1) / alignment * alignment
        }

        @Throws(IOException::class)
        private fun toNativeImage(image: BufferedImage): NativeImage {
            ImageIO.setUseCache(false)
            val output = ByteArrayOutputStream()
            if (!ImageIO.write(image, "png", output)) {
                throw IOException("No PNG writer is available")
            }
            val buffer = BufferUtils.createByteBuffer(output.size()).put(output.toByteArray())
            buffer.flip()
            return NativeImage.read(buffer)
        }
    }
}
