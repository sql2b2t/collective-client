package me.collective.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import java.util.SequencedMap
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4fc

/**
 * Compatibility shims for 1.21.11 Tesselator and MultiBufferSource removed in 26.2.
 */
class Tesselator private constructor(capacity: Int = 786432) {
    private val byteBuffer: ByteBufferBuilder = ByteBufferBuilder(capacity)

    fun begin(topology: PrimitiveTopology, format: VertexFormat): BufferBuilder {
        return BufferBuilder(byteBuffer, topology, format)
    }

    companion object {
        private val INSTANCE = Tesselator()

        @JvmStatic
        fun getInstance(): Tesselator = INSTANCE
    }
}

interface MultiBufferSource {
    fun getBuffer(renderType: RenderType): VertexConsumer

    open class BufferSource(
        protected val sharedBuffer: ByteBufferBuilder,
        protected val fixedBuffers: SequencedMap<RenderType, BufferBuilder> = LinkedHashMap()
    ) : MultiBufferSource, BufferSourceCleanup {
        private val activeBuilders = LinkedHashMap<RenderType, BufferBuilder>()

        override fun `collective$cleanAll`() {
            for (builder in activeBuilders.values) {
                builder.build()?.close()
            }
            activeBuilders.clear()
        }

        override fun getBuffer(renderType: RenderType): VertexConsumer {
            return fixedBuffers[renderType] ?: activeBuilders.getOrPut(renderType) {
                BufferBuilder(sharedBuffer, renderType.primitiveTopology(), renderType.format())
            }
        }

        open fun endBatch() {
            val entries = activeBuilders.entries.toList()
            activeBuilders.clear()
            for ((rt, builder) in entries) {
                val mesh = builder.build() ?: continue
                MeshBuffer.drawMesh(rt, mesh)
            }
        }

        open fun endBatch(renderType: RenderType) {
            val builder = activeBuilders.remove(renderType) ?: return
            val mesh = builder.build() ?: return
            MeshBuffer.drawMesh(renderType, mesh)
        }
    }

    companion object {
        @JvmStatic
        fun immediate(byteBuffer: ByteBufferBuilder = ByteBufferBuilder(786432)): BufferSource {
            return BufferSource(byteBuffer)
        }
    }
}

fun drawTextInBatch(
    font: Font,
    text: FormattedCharSequence,
    x: Float,
    y: Float,
    color: Int,
    shadow: Boolean,
    pose: Matrix4fc,
    bufferSource: MultiBufferSource,
    displayMode: Font.DisplayMode,
    backgroundColor: Int,
    packedLight: Int
) {
    val prepared = font.prepareText(text, x, y, color, shadow, false, backgroundColor)
    prepared.visit(object : Font.GlyphVisitor {
        override fun acceptRenderable(renderable: TextRenderable) {
            renderable.render(pose, bufferSource.getBuffer(renderable.renderType(displayMode)), packedLight, false)
        }
    })
}

fun drawTextInBatch(
    font: Font,
    text: Component,
    x: Float,
    y: Float,
    color: Int,
    shadow: Boolean,
    pose: Matrix4fc,
    bufferSource: MultiBufferSource,
    displayMode: Font.DisplayMode,
    backgroundColor: Int,
    packedLight: Int
) {
    drawTextInBatch(font, text.visualOrderText, x, y, color, shadow, pose, bufferSource, displayMode, backgroundColor, packedLight)
}

fun drawTextInBatch(
    font: Font,
    text: String,
    x: Float,
    y: Float,
    color: Int,
    shadow: Boolean,
    pose: Matrix4fc,
    bufferSource: MultiBufferSource,
    displayMode: Font.DisplayMode,
    backgroundColor: Int,
    packedLight: Int
) {
    val prepared = font.prepareText(text, x, y, color, shadow, backgroundColor)
    prepared.visit(object : Font.GlyphVisitor {
        override fun acceptRenderable(renderable: TextRenderable) {
            renderable.render(pose, bufferSource.getBuffer(renderable.renderType(displayMode)), packedLight, false)
        }
    })
}
