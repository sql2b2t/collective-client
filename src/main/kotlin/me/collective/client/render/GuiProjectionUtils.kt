package me.collective.client.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix3x2fStack
import org.joml.Matrix4f

object GuiProjectionUtils : ClientAccess {
    private var savedModelView: Matrix4f? = null

    @JvmStatic
    fun restoreProjection() {
        val modelView = savedModelView ?: return
        RenderSystem.restoreProjectionMatrix()
        RenderSystem.getModelViewMatrix().set(modelView)
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
        RenderSystem.backupProjectionMatrix()
        val modelView = RenderSystem.getModelViewMatrix()
        savedModelView = Matrix4f(modelView)
        val ortho = Matrix4f().setOrtho(0.0f, guiWidth, guiHeight, 0.0f, 1000.0f, 11000.0f)
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC)
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
    fun toPoseStack(graphics: GuiGraphics): PoseStack {
        return graphics.pose()
    }
}
