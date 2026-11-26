package me.collective.client.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import org.joml.Matrix3x2fStack
import org.joml.Matrix4f

object GuiProjectionUtils : ClientAccess {
    @JvmField
    val GUI_PROJECTION_CACHE: ProjectionMatrixBuffer = ProjectionMatrixBuffer("gui_collective")

    private var savedProjectionBuffer: GpuBufferSlice? = null
    private var savedProjectionType: ProjectionType? = null
    private var savedModelView: Matrix4f? = null

    @JvmStatic
    fun restoreProjection() {
        val modelView = savedModelView ?: return
        RenderSystem.setProjectionMatrix(savedProjectionBuffer!!, savedProjectionType!!)
        RenderSystem.getModelViewStack().set(modelView)
        savedProjectionBuffer = null
        savedProjectionType = null
        savedModelView = null
    }

    @JvmStatic
    fun beginGuiProjection() {
        if (savedModelView != null) {
            return
        }
        val window = ClientAccess.collectiveMinecraft.window
        val guiWidth = window.width.toFloat() / window.guiScale.toFloat()
        val guiHeight = window.height.toFloat() / window.guiScale.toFloat()
        savedProjectionBuffer = RenderSystem.getProjectionMatrixBuffer()
        savedProjectionType = RenderSystem.getProjectionType()
        val modelView = RenderSystem.getModelViewStack()
        savedModelView = Matrix4f(modelView)
        val ortho = Matrix4f().setOrtho(0.0f, guiWidth, guiHeight, 0.0f, 1000.0f, 11000.0f)
        RenderSystem.setProjectionMatrix(
            GUI_PROJECTION_CACHE.getBuffer(ortho),
            ProjectionType.ORTHOGRAPHIC
        )
        modelView.identity().translate(0.0f, 0.0f, -11000.0f)
    }

    @JvmStatic
    fun toPoseStack(guiPose: Matrix3x2fStack): PoseStack {
        val poseStack = PoseStack()
        val pose = poseStack.last()
        val matrix = pose.pose()
        matrix.set(
            guiPose.m00(), guiPose.m01(), 0.0f, 0.0f,
            guiPose.m10(), guiPose.m11(), 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            guiPose.m20(), guiPose.m21(), 0.0f, 1.0f
        )
        return poseStack
    }

    @JvmStatic
    fun toPoseStack(graphics: GuiGraphicsExtractor): PoseStack {
        return toPoseStack(graphics.pose())
    }
}
