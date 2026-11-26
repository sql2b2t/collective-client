package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.PrimitiveTopology
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

object LineMeshRenderer : ClientAccess {
    private val projectionMatrix = Matrix4f()
    private val inverseProjectionMatrix = Matrix4f()
    private val positionVector = Vector3f()
    private val projectedPosition = Vector2f()
    private val tracerPosition = Vector2f()
    private val clipPosition = Vector4f()
    private val pendingPosition = Vector3f()

    @JvmField
    internal val vertices: MeshBuffer = MeshBuffer.create()

    @JvmField
    internal var poseStack: PoseStack? = null

    private var pendingColor = 0
    private var pendingVertex = false
    private var lineWidth = 0f

    @JvmStatic
    fun updateProjection(poses: PoseStack) {
        projectionMatrix.set(RenderUtils.WORLD_PROJECTION_MATRIX)
            .mul(poses.last().pose())
        inverseProjectionMatrix.set(projectionMatrix)
            .invert()
    }

    @JvmStatic
    fun begin(event: WorldRenderEvent, width: Float) {
        begin(event.poseStack!!, width)
    }

    @JvmStatic
    fun begin(poses: PoseStack, width: Float) {
        vertices.begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        poseStack = poses
        pendingVertex = false
        lineWidth = width
        updateProjection(poses)
    }

    @JvmStatic
    fun addVertex(x: Double, y: Double, z: Double, color: Color) {
        addVertex(x, y, z, color.hashCode())
    }

    @JvmStatic
    fun addVertex(x: Double, y: Double, z: Double, color: Int) {
        addVertex(x, y, z, color, false, false)
    }

    @JvmStatic
    fun addVertex(
        x: Double,
        y: Double,
        z: Double,
        color: Int,
        relativeToCamera: Boolean,
        clamp: Boolean
    ) {
        var vx = x
        var vy = y
        var vz = z
        if (relativeToCamera) {
            val cameraPos = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position()
            vx = (vx - cameraPos.x()).toFloat().toDouble()
            vy = (vy - cameraPos.y()).toFloat().toDouble()
            vz = (vz - cameraPos.z()).toFloat().toDouble()
        }

        if (clamp) {
            val clampedPosition = clampToViewport(vx.toFloat(), vy.toFloat(), vz.toFloat())
            vx = clampedPosition.x.toDouble()
            vy = clampedPosition.y.toDouble()
            vz = clampedPosition.z.toDouble()
        }

        if (pendingVertex) {
            VertexGeometry.appendLine(
                vertices, poseStack!!.last(), pendingPosition.x(), pendingPosition.y(), pendingPosition.z(),
                vx.toFloat(), vy.toFloat(), vz.toFloat(), lineWidth, pendingColor, color
            )
        } else {
            pendingPosition.set(vx, vy, vz)
            pendingColor = color
        }
        pendingVertex = !pendingVertex
    }

    @JvmStatic
    fun addTracer(position: Vec3, color: Color) {
        addTracer(position, color.hashCode())
    }

    @JvmStatic
    fun addTracer(position: Vec3, color: Int) {
        val projected = projectTracer(position) ?: return

        val origin = unproject(0.0f, 0.0f)
        val target = unproject(projected.x, projected.y)
        addVertex(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble(), color, false, false)
        addVertex(target.x.toDouble(), target.y.toDouble(), target.z.toDouble(), color, false, false)
    }

    @JvmStatic
    fun addLine(from: Vec3, to: Vec3, color: Color) {
        addLine(from, to, color.hashCode())
    }

    @JvmStatic
    fun addLine(from: Vec3, to: Vec3, color: Int) {
        addVertex(from.x, from.y, from.z, color, true, false)
        addVertex(to.x, to.y, to.z, color, true, true)
    }

    @JvmStatic
    fun draw() {
        vertices.draw(ClientRenderTypes.worldLines())
    }

    private fun clampToViewport(x: Float, y: Float, z: Float): Vector3f {
        positionVector.set(x, y, z)
        projectionMatrix.transform(clipPosition.set(positionVector, 1.0f))
        if (abs(clipPosition.w) < 1.0e-6f) {
            return positionVector
        }

        val inverseW = 1.0f / clipPosition.w
        val projectedX = clipPosition.x * inverseW
        val projectedY = clipPosition.y * inverseW
        val outsideViewport = clipPosition.w <= 0.0f ||
            projectedX < -1.0f ||
            projectedX > 1.0f ||
            projectedY < -1.0f ||
            projectedY > 1.0f
        if (!outsideViewport) {
            return positionVector
        }

        projectedPosition.set(projectedX, projectedY)
        val edgeScale = min(
            1.0f / abs(projectedPosition.x),
            1.0f / abs(projectedPosition.y)
        )
        projectedPosition.mul(edgeScale)
        clipPosition.set(
            projectedPosition.x,
            projectedPosition.y,
            clipPosition.z * inverseW,
            1.0f
        )
        clipPosition.mul(inverseProjectionMatrix)
        if (clipPosition.w == 0.0f) {
            return positionVector
        }

        val clampedPosition = Vector3f(
            clipPosition.x,
            clipPosition.y,
            clipPosition.z
        ).div(clipPosition.w)
        return if (clampedPosition.x.isFinite() &&
            clampedPosition.y.isFinite() &&
            clampedPosition.z.isFinite()
        ) {
            clampedPosition
        } else {
            positionVector
        }
    }

    private fun unproject(x: Float, y: Float): Vector3f {
        clipPosition.set(x, y, 0.0f, 1.0f)
        inverseProjectionMatrix.transform(clipPosition)
        return Vector3f(clipPosition.x, clipPosition.y, clipPosition.z).div(clipPosition.w)
    }

    private fun projectTracer(position: Vec3): Vector2f? {
        val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position()
        projectionMatrix.transform(
            clipPosition.set(
                (position.x - cameraPosition.x).toFloat(),
                (position.y - cameraPosition.y).toFloat(),
                (position.z - cameraPosition.z).toFloat(),
                1.0f
            )
        )
        val w = clipPosition.w
        val behind = w <= 1.0e-4f
        var x = if (behind) clipPosition.x else clipPosition.x / w
        var y = if (behind) clipPosition.y else clipPosition.y / w
        var extent = max(abs(x), abs(y))
        if (behind && extent < 1.0e-6f) {
            x = 0.0f
            y = -1.0f
            extent = 1.0f
        }

        if (behind || extent > 1.0f) {
            x /= extent
            y /= extent
        } else if (extent < 1.0e-5f) {
            return null
        }

        return tracerPosition.set(x, y)
    }
}
