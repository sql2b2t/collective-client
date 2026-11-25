package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.client.HUDModule
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.math.MathUtils
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

open class ResizableHudWidgetRenderer(widget: HudWidgetModule) : HudWidgetRenderer(widget) {

    private val sizeSetting: Setting<Float> = NumberSetting(
        "Size",
        1.0f,
        0.5f,
        2.0f
    )
    private val handleAnimation = FloatAnimation()
    private var applyHudScale: Boolean = false
    private var resizingEnabled: Boolean = false

    val scale: Float
        get() {
            val hud = hudSettings()
            val scaleMultiplier = if (applyHudScale && hud != null) hud.getTextScale() else 1.0f
            return sizeSetting.value * scaleMultiplier
        }

    private fun isMouseButtonPressed(button: Int): Boolean {
        return GLFW.glfwGetMouseButton(
            ClientAccess.collectiveMinecraft.window.window,
            button
        ) == 1
    }

    override fun calculateDimensions(): FloatArray {
        val dimensions = widget.calculateDimensions()
        val currentScale = scale
        dimensions[0] *= currentScale
        dimensions[1] *= currentScale
        return dimensions
    }

    override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
        super.render(poses, mouseX, mouseY)
        val sourceDimensions = widget.calculateDimensions()
        val currentScale = scale
        poses.pushPose()
        poses.scale(currentScale, currentScale, 1.0f)
        poses.translate(getX() / currentScale, getY() / currentScale, 0.0f)
        widget.renderContent(poses)
        if (!renderingHud && resizingEnabled) {
            val handleHovered = isResizeHandleHovered(mouseX, mouseY)
            if (activeResizeRenderer == this) {
                val contentWidth = sourceDimensions[0].toDouble()
                val contentHeight = sourceDimensions[1].toDouble()
                val resizeScale = min(
                    (mouseX - getX()) / contentWidth,
                    (mouseY - getY()) / contentHeight
                )
                sizeSetting.value = MathUtils.floorToDecimals(resizeScale, 2).toFloat()
            }

            if (handleHovered && isMouseButtonPressed(1)) {
                sizeSetting.value = 1.0f
            }

            if (handleHovered && isMouseButtonPressed(0)) {
                activeResizeRenderer = this
            } else if (!isMouseButtonPressed(0)) {
                activeResizeRenderer = null
            }

            if (activeResizeRenderer == this) {
                handleAnimation.setTarget(0.6f, 250L)
            } else if (handleHovered) {
                handleAnimation.setTarget(0.4f, 250L)
            } else {
                handleAnimation.setTarget(0.2f, 250L)
            }

            val handleColor = ColorUtils.toArgb(
                ColorUtils.getThemeColor(),
                handleAnimation.value * ClientContext.getHudEditor().getTransitionOpacity()
            )
            val handleVertices = RenderUtils.getEntityBuffers().getBuffer(ClientRenderTypes.guiTriangles())
            handleVertices.addVertex(
                poses.last(),
                sourceDimensions[0] - 5.0f,
                sourceDimensions[1] + 0.0f,
                0.0f
            ).setColor(handleColor)
            handleVertices.addVertex(
                poses.last(),
                sourceDimensions[0] + 0.0f,
                sourceDimensions[1] + 0.0f,
                0.0f
            ).setColor(handleColor)
            handleVertices.addVertex(
                poses.last(),
                sourceDimensions[0] + 0.0f,
                sourceDimensions[1] - 5.0f,
                0.0f
            ).setColor(handleColor)
        }

        poses.popPose()
    }

    fun isResizeHandleHovered(mouseX: Double, mouseY: Double): Boolean {
        if (!resizingEnabled) {
            return false
        }

        val currentDimensions = getDimensions()
        val right = getX() + max(currentDimensions[0], 4.0f)
        val bottom = getY() + max(currentDimensions[1], 4.0f)
        return mouseX >= right - 5.0f &&
            mouseX <= right &&
            mouseY >= bottom - 5.0f &&
            mouseY <= bottom
    }

    fun enableResizing(widget: HudWidgetModule) {
        resizingEnabled = true
        widget.register(sizeSetting)
    }

    fun enableHudScale() {
        applyHudScale = true
    }

    companion object {
        private var hudSettings: HUDModule? = null

        private fun hudSettings(): HUDModule? {
            if (hudSettings == null) {
                hudSettings = ClientContext.getModuleOrNull(HUDModule::class.java)
            }
            return hudSettings
        }

        init {
            ResourceLocation.fromNamespaceAndPath("collective", "textures/triangle.png")
        }

        private var activeResizeRenderer: ResizableHudWidgetRenderer? = null
    }
}
