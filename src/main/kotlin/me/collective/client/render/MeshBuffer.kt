package me.collective.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.PrimitiveTopology
import net.minecraft.client.renderer.rendertype.RenderType

open class MeshBuffer protected constructor(capacity: Int) : VertexConsumer {
    private val byteBuffer: ByteBufferBuilder = ByteBufferBuilder(capacity)

    @JvmField
    protected var builder: BufferBuilder? = null
    private var mode: PrimitiveTopology? = null
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

    override fun setLineWidth(lineWidth: Float): VertexConsumer =
        getBuilder().setLineWidth(lineWidth)

    override fun setColor(color: Int): VertexConsumer =
        getBuilder().setColor(color)

    override fun setUv(u: Float, v: Float): VertexConsumer =
        getBuilder().setUv(u, v)

    fun isEmpty(): Boolean = builder == null

    fun setFormat(mode: PrimitiveTopology, format: VertexFormat): MeshBuffer {
        this.mode = mode
        this.format = format
        return this
    }

    fun begin(mode: PrimitiveTopology, format: VertexFormat): BufferBuilder {
        val newBuilder = BufferBuilder(byteBuffer, mode, format)
        builder = newBuilder
        return newBuilder
    }

    fun draw(renderType: RenderType) {
        val currentBuilder = builder ?: return
        val mesh = currentBuilder.build()
        builder = null
        if (mesh != null) {
            drawMesh(renderType, mesh)
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
        fun drawMesh(renderType: RenderType, mesh: MeshData) {
            mesh.use { m ->
                val prepared = renderType.prepare()
                val scissor = prepared.scissorState()
                if (scissor.enabled() && (scissor.width() <= 0 || scissor.height() <= 0)) {
                    return
                }
                val drawState = m.drawState()
                val device = RenderSystem.getDevice()
                device.createBuffer({ "collective_mesh_vb" }, GpuBuffer.USAGE_VERTEX, m.vertexBuffer()).use { vb ->
                    val rawIb = m.indexBuffer()
                    if (rawIb != null) {
                        device.createBuffer({ "collective_mesh_ib" }, GpuBuffer.USAGE_INDEX, rawIb).use { ib ->
                            prepared.drawFromBuffer(vb, ib, drawState.indexType(), 0, 0, drawState.indexCount())
                        }
                    } else {
                        val seq = RenderSystem.getSequentialBuffer(drawState.primitiveTopology())
                        val ib = seq.getBuffer(drawState.indexCount())
                        prepared.drawFromBuffer(vb, ib, seq.type(), 0, 0, drawState.indexCount())
                    }
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
