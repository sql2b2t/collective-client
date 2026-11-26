package me.collective.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import me.collective.client.modules.render.ChamsModule
import net.minecraft.util.Mth

open class ChamsVertexConsumer : VertexConsumer {
    private val quadX = FloatArray(4)
    private val quadY = FloatArray(4)
    private val quadZ = FloatArray(4)
    private val quadU = FloatArray(4)
    private val quadV = FloatArray(4)
    private var vertexCount = 0

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        if (this.vertexCount >= 4) {
            this.vertexCount = 0
        }

        quadX[this.vertexCount] = x
        quadY[this.vertexCount] = y
        quadZ[this.vertexCount] = z
        this.vertexCount++
        if (ChamsModule.renderingShineModels) {
            return this
        }

        if (this.vertexCount == 4) {
            if (EntityChamsRenderer.hasVisibleFill()) {
                for (vertex in 0 until 4) {
                    EntityChamsRenderer.fillMesh
                        .addVertex(offsetX + quadX[vertex], offsetY + quadY[vertex], offsetZ + quadZ[vertex])
                        .setColor(EntityChamsRenderer.fillColor.hashCode())
                }
            }

            if (EntityChamsRenderer.hasVisibleOutline()) {
                this.emitOutlineSegment(
                    offsetX + quadX[0], offsetY + quadY[0], offsetZ + quadZ[0],
                    offsetX + quadX[1], offsetY + quadY[1], offsetZ + quadZ[1]
                )
                this.emitOutlineSegment(
                    offsetX + quadX[1], offsetY + quadY[1], offsetZ + quadZ[1],
                    offsetX + quadX[2], offsetY + quadY[2], offsetZ + quadZ[2]
                )
                this.emitOutlineSegment(
                    offsetX + quadX[2], offsetY + quadY[2], offsetZ + quadZ[2],
                    offsetX + quadX[3], offsetY + quadY[3], offsetZ + quadZ[3]
                )
                this.emitOutlineSegment(
                    offsetX + quadX[3], offsetY + quadY[3], offsetZ + quadZ[3],
                    offsetX + quadX[0], offsetY + quadY[0], offsetZ + quadZ[0]
                )
            }

            this.vertexCount = 0
        }

        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this

    override fun setColor(argb: Int): VertexConsumer = this

    override fun setUv(u: Float, v: Float): VertexConsumer {
        if (!ChamsModule.renderingShineModels) {
            return this
        }

        if (this.vertexCount == 0) {
            return this
        }

        quadU[this.vertexCount - 1] = u
        quadV[this.vertexCount - 1] = v
        if (this.vertexCount == 4) {
            this.emitTexturedQuad(EntityChamsRenderer.relation.meshBuffer)
            this.vertexCount = 0
        }

        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer = this

    override fun setUv2(u: Int, v: Int): VertexConsumer = this

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this

    override fun setLineWidth(width: Float): VertexConsumer = this

    private fun emitTexturedQuad(consumer: VertexConsumer) {
        for (vertex in 0 until 4) {
            consumer.addVertex(offsetX + quadX[vertex], offsetY + quadY[vertex], offsetZ + quadZ[vertex])
                .setUv(quadU[vertex], quadV[vertex])
        }
    }

    open fun emitOutlineSegment(
        startX: Float,
        startY: Float,
        startZ: Float,
        endX: Float,
        endY: Float,
        endZ: Float
    ) {
        val deltaX = endX - startX
        val deltaY = endY - startY
        val deltaZ = endZ - startZ
        val length = Mth.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        if (length < 1.0e-6f) {
            return
        }
        val normalX = deltaX / length
        val normalY = deltaY / length
        val normalZ = deltaZ / length
        EntityChamsRenderer.outlineMesh.addVertex(startX, startY, startZ)
            .setNormal(normalX, normalY, normalZ).setLineWidth(EntityChamsRenderer.getLineWidth())
            .setColor(EntityChamsRenderer.outlineColor.hashCode())
        EntityChamsRenderer.outlineMesh.addVertex(endX, endY, endZ)
            .setNormal(normalX, normalY, normalZ).setLineWidth(EntityChamsRenderer.getLineWidth())
            .setColor(EntityChamsRenderer.outlineColor.hashCode())
    }

    companion object {
        @JvmField
        internal var offsetX: Float = 0f

        @JvmField
        internal var offsetY: Float = 0f

        @JvmField
        internal var offsetZ: Float = 0f
    }
}
