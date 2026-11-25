package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.awt.Color
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import net.minecraft.core.BlockPos
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB

object WorldBoxRenderer : ClientAccess {
    @JvmField
    internal val fillVertices: MeshBuffer = MeshBuffer.create()

    @JvmField
    internal val lineVertices: MeshBuffer = MeshBuffer.create()

    init {
        ClientAccess.eventBus.subscribeStatic(WorldBoxRenderer::class.java)
        fillVertices.setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        lineVertices.setFormat(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
    }

    @JvmStatic
    @EventHandler(priority = -9999)
    private fun onRenderEnd(event: WorldRenderEvent.Main) {
        flush()
        GuiDrawUtils.flush(true)
    }

    @JvmStatic
    fun fillBox(poses: PoseStack?, position: BlockPos, color: Color?) {
        if (poses == null || color == null) return
        fillBox(poses, AABB(position), color)
    }

    @JvmStatic
    fun fillBox(poses: PoseStack?, bounds: AABB, color: Color?) {
        if (poses == null || color == null) return
        fillBox(poses, bounds, color.hashCode())
    }

    @JvmStatic
    fun fillBox(poses: PoseStack?, bounds: AABB, color: Int) {
        if (poses != null && ARGB.alpha(color) != 0) {
            val poseMatrix = poses.last().pose()
            VertexGeometry.appendFilledBox(fillVertices, poseMatrix, bounds, color)
        }
    }

    @JvmStatic
    fun fillFadingBox(poses: PoseStack?, bounds: AABB, color: Color?) {
        if (poses != null && color != null && color.alpha != 0) {
            val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position
            val minX = (bounds.minX - cameraPosition.x()).toFloat()
            val minY = (bounds.minY - cameraPosition.y()).toFloat()
            val minZ = (bounds.minZ - cameraPosition.z()).toFloat()
            val maxX = (bounds.maxX - cameraPosition.x()).toFloat()
            val maxY = (bounds.maxY - cameraPosition.y()).toFloat()
            val maxZ = (bounds.maxZ - cameraPosition.z()).toFloat()
            val bottomColor = color.hashCode()
            val topColor = ColorUtils.toArgb(color, 0)
            val poseMatrix = poses.last().pose()
            fillVertices.addVertex(poseMatrix, minX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, minX, minY, minZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(bottomColor)
            fillVertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(topColor)
            fillVertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(topColor)
        }
    }

    @JvmStatic
    fun outlineBox(poses: PoseStack?, position: BlockPos, color: Color?, lineWidth: Float) {
        if (poses == null || color == null) return
        outlineBox(poses, AABB(position), color, lineWidth)
    }

    @JvmStatic
    fun outlineBox(poses: PoseStack?, bounds: AABB, color: Color?, lineWidth: Float) {
        if (poses == null || color == null) return
        outlineBox(poses, bounds, color.hashCode(), lineWidth)
    }

    @JvmStatic
    fun outlineBox(poses: PoseStack?, bounds: AABB, color: Int, lineWidth: Float) {
        if (poses != null && lineWidth > 0.0f && ARGB.alpha(color) > 0) {
            VertexGeometry.appendBoxOutline(lineVertices, poses.last(), bounds, lineWidth, color)
        }
    }

    @JvmStatic
    fun outlineFadingBox(poses: PoseStack?, bounds: AABB, color: Int, lineWidth: Float) {
        if (poses != null && lineWidth > 0.0f && ARGB.alpha(color) > 0) {
            val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position
            val minX = (bounds.minX - cameraPosition.x()).toFloat()
            val minY = (bounds.minY - cameraPosition.y()).toFloat()
            val minZ = (bounds.minZ - cameraPosition.z()).toFloat()
            val maxX = (bounds.maxX - cameraPosition.x()).toFloat()
            val maxY = (bounds.maxY - cameraPosition.y()).toFloat()
            val maxZ = (bounds.maxZ - cameraPosition.z()).toFloat()
            val topColor = ARGB.color(0, color)
            val pose = poses.last()
            VertexGeometry.appendLine(lineVertices, pose, minX, minY, minZ, minX, maxY, minZ, lineWidth, color, topColor)
            VertexGeometry.appendLine(lineVertices, pose, maxX, minY, minZ, maxX, maxY, minZ, lineWidth, color, topColor)
            VertexGeometry.appendLine(lineVertices, pose, minX, minY, maxZ, minX, maxY, maxZ, lineWidth, color, topColor)
            VertexGeometry.appendLine(lineVertices, pose, maxX, minY, maxZ, maxX, maxY, maxZ, lineWidth, color, topColor)
        }
    }

    @JvmStatic
    fun applyRenderColor(color: Int): Int {
        return modulateColor(color, RenderUtils.getRenderColor())
    }

    @JvmStatic
    fun modulateColor(color: Int, components: IntArray): Int {
        return ColorUtils.modulateArgb(color, components)
    }

    @JvmStatic
    fun flush() {
        fillVertices.draw(ClientRenderTypes.worldFill())
        lineVertices.draw(ClientRenderTypes.worldLines())
    }
}
