package me.collective.client.render

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import net.minecraft.client.renderer.RenderType

open class MeshBuffer protected constructor(capacity: Int) : VertexConsumer {
    private val byteBuffer: ByteBufferBuilder = ByteBufferBuilder(capacity)

    @JvmField
    protected var builder: BufferBuilder? = null
    private var mode: Mode? = null
    private var format: VertexFormat? = null

    fun getBuilder(): BufferBuilder {
        var currentBuilder = builder
        if (currentBuilder == null) {
            val currentMode = mode
            val currentFormat = format
            if (currentMode == null || currentFormat == null) {
                throw IllegalArgumentException("Tried to invoke empty not caching buffer")
            }

            currentBuilder = BufferBuilder(
                byteBuffer,
                currentMode,
                currentFormat
            )
            builder = currentBuilder
        }

        return currentBuilder
    }

    fun isBuilding(): Boolean = builder != null

    fun build(): MeshData {
        val currentBuilder = builder ?: throw IllegalArgumentException("Tried to end empty buffer")
        val mesh = currentBuilder.build()!!
        builder = null
        return mesh
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer =
        getBuilder().setNormal(x, y, z)

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer =
        getBuilder().addVertex(x, y, z)

    override fun setColor(color: Int): VertexConsumer =
        getBuilder().setColor(color)

    override fun setUv(u: Float, v: Float): VertexConsumer =
        getBuilder().setUv(u, v)

    fun isEmpty(): Boolean = builder == null

    fun setFormat(mode: Mode, format: VertexFormat): MeshBuffer {
        this.mode = mode
        this.format = format
        return this
    }

    fun begin(mode: Mode, format: VertexFormat): BufferBuilder {
        val newBuilder = BufferBuilder(byteBuffer, mode, format)
        builder = newBuilder
        return newBuilder
    }

    fun draw(renderType: RenderType) {
        val currentBuilder = builder ?: return
        val mesh = currentBuilder.build()
        builder = null
        if (mesh != null) {
            renderType.draw(mesh)
        }
    }

    override fun setUv1(overlayU: Int, overlayV: Int): VertexConsumer =
        getBuilder().setUv1(overlayU, overlayV)

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer =
        getBuilder().setColor(red, green, blue, alpha)

    override fun setUv2(lightU: Int, lightV: Int): VertexConsumer =
        getBuilder().setUv2(lightU, lightV)

    fun draw() {
        val currentBuilder = builder ?: return
        draw(currentBuilder)
        builder = null
    }

    companion object {
        @JvmStatic
        fun create(capacity: Int): MeshBuffer = MeshBuffer(capacity)

        @JvmStatic
        fun create(): MeshBuffer = create(786432)

        @JvmStatic
        fun draw(builder: BufferBuilder?) {
            if (builder != null) {
                val mesh = builder.build()
                if (mesh != null) {
                    MeshDrawContext.draw(mesh)
                }
            }
        }

        @JvmStatic
        fun draw(buffer: MeshBuffer) {
            if (buffer.isBuilding()) {
                draw(buffer.builder)
                buffer.builder = null
            }
        }
    }
}
