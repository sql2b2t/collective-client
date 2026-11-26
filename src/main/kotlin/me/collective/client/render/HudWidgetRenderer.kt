package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.CursorType
import me.collective.client.gui.GuiElement
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.screens.HudEditorScreen
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.settings.options.HudAnchor
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.math.FloatBounds
import net.minecraft.util.Mth
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class HudWidgetRenderer(
    @JvmField protected val widget: HudWidgetModule
) : ClientAccess, GuiElement {

    private val selectionAnimation = FloatAnimation()
    private val enabledAnimation = FloatAnimation()

    private var _anchor: HudAnchor = HudAnchor.NONE
    var anchor: HudAnchor
        @JvmName("getAnchorProp") get() = _anchor
        @JvmName("setAnchorProp") set(value) { _anchor = value }

    var horizontalAlignment: HudHorizontalAlignment = HudHorizontalAlignment.FREE
    var verticalAlignment: HudVerticalAlignment = HudVerticalAlignment.FREE

    var relativeX: Float = 0.5f
        private set
    var relativeY: Float = 0.5f
        private set

    private var _horizontalDirection: Int = 0
    private var _verticalDirection: Int = 0

    private var _dimensions: FloatArray = FloatArray(2)
    var dimensions: FloatArray
        @JvmName("getDimensionsProp") get() = _dimensions
        @JvmName("setDimensionsProp") set(value) { _dimensions = value }

    var isSelected: Boolean = false
        set(selected) {
            if (field != selected) {
                selectionAnimation.setTarget(if (selected) 1.0f else 0.0f, 250L)
                field = selected
            }
        }

    private var hoverStartMillis: Long = 0L

    override val height: Int
        get() {
            var h = FontRenderer.INSTANCE.getHeight()
            if (FontsModule.INSTANCE?.isToggled() == true) {
                h--
            }
            return h
        }

    val y: Float
        @JvmName("getYProp") get() = getY()

    val x: Float
        @JvmName("getXProp") get() = getX()

    val enabledProgress: Float
        get() = enabledAnimation.value

    val isEnabled: Boolean
        @JvmName("isEnabledProp") get() = isEnabled()

    val bounds: FloatBounds
        get() = FloatBounds.fromPositionAndSize(
            relativeX * ClientAccess.collectiveMinecraft.window.guiScaledWidth,
            relativeY * ClientAccess.collectiveMinecraft.window.guiScaledHeight,
            _dimensions[0],
            _dimensions[1]
        )

    val verticalDirection: Int
        @JvmName("getVerticalDirectionProp")
        get() {
            if (_verticalDirection == 0) {
                updateDirections()
            }
            return _verticalDirection
        }

    fun getVerticalDirection(): Int = verticalDirection

    val widgetModule: HudWidgetModule
        get() = widget

    val horizontalDirection: Int
        @JvmName("getHorizontalDirectionProp")
        get() {
            if (_horizontalDirection == 0) {
                updateDirections()
            }
            return _horizontalDirection
        }

    fun getHorizontalDirection(): Int = horizontalDirection

    fun getAnchor(): HudAnchor = _anchor

    fun setAnchor(anchor: HudAnchor) {
        _anchor = anchor
    }

    fun getDimensions(): FloatArray = _dimensions

    fun setDimensions(dimensions: FloatArray) {
        _dimensions = dimensions
    }

    fun getX(): Float {
        return min(
            relativeX * ClientAccess.collectiveMinecraft.window.guiScaledWidth,
            ClientAccess.collectiveMinecraft.window.guiScaledWidth - _dimensions[0]
        )
    }

    fun getY(): Float {
        return min(
            relativeY * ClientAccess.collectiveMinecraft.window.guiScaledHeight,
            ClientAccess.collectiveMinecraft.window.guiScaledHeight - _dimensions[1]
        )
    }

    open fun isEnabled(): Boolean = widget.isToggled()

    fun updateSnapping(screenWidth: Float, screenHeight: Float, snapDistance: Float) {
        val currentX = getX()
        val currentY = getY()
        val width = _dimensions[0]
        val height = _dimensions[1]
        val centerX = currentX + width / 2.0f
        val centerY = currentY + height / 2.0f
        resetSnapping()

        if (currentX < snapDistance) {
            horizontalAlignment = HudHorizontalAlignment.LEFT
        } else if (currentX + width > screenWidth - snapDistance) {
            horizontalAlignment = HudHorizontalAlignment.RIGHT
        } else if (abs(centerX - screenWidth / 2.0f) <= snapDistance) {
            horizontalAlignment = HudHorizontalAlignment.CENTER
        }

        if (currentY < snapDistance) {
            verticalAlignment = HudVerticalAlignment.TOP
        } else if (currentY + height > screenHeight - snapDistance) {
            verticalAlignment = HudVerticalAlignment.BOTTOM
        } else if (abs(centerY - screenHeight / 2.0f) <= snapDistance) {
            verticalAlignment = HudVerticalAlignment.CENTER
        }
    }

    abstract fun calculateDimensions(): FloatArray

    override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
        val size = _dimensions
        if (isMouseOver(mouseX, mouseY) && !isSelected) {
            if (hoverStartMillis == -1L) {
                hoverStartMillis = System.currentTimeMillis()
            }

            if (hoverStartMillis + 300L <= System.currentTimeMillis()) {
                val labelX = if (horizontalDirection == 1) {
                    getX() + size[0] + 2.0f
                } else {
                    getX() - 2.0f - FontRenderer.INSTANCE.getWidth(widget.name)
                }
                HudEditorScreen.hoverLabel.show(
                    widget.name,
                    labelX,
                    getY(),
                    horizontalDirection
                )
            }
        } else {
            hoverStartMillis = -1L
        }

        if (!renderingHud) {
            if (isMouseOver(mouseX, mouseY)) {
                ClientScreen.cursor = CursorType.POINTER
            }

            val currentX = getX()
            val currentY = getY()
            val editorScale = RenderUtils.getRenderColor()[3] / 255.0f
            val backgroundAlpha = ClientContext.getHudEditor().getTransitionOpacity() *
                editorScale *
                editorBackgroundColor.alpha / 255.0f
            GuiDrawUtils.fillRect(
                poses,
                currentX,
                currentY,
                currentX + max(size[0], 4.0f),
                currentY + max(size[1], 4.0f),
                ColorUtils.toArgb(editorBackgroundColor, backgroundAlpha)
            )
            val selectionProgress = selectionAnimation.value
            if (selectionProgress > 0.0f) {
                GuiDrawUtils.drawRectOutline(
                    poses,
                    currentX - 1.0f,
                    currentY - 1.0f,
                    currentX + max(size[0], 4.0f),
                    currentY + max(size[1], 4.0f),
                    ColorUtils.withAlpha(
                        ColorUtils.getThemeColor(),
                        (selectionProgress * 255.0f).toInt()
                    )
                )
            }

            if (editorScale != 1.0f) {
                GuiDrawUtils.flush()
            }
        }
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        val size = _dimensions
        val currentX = getX()
        val currentY = getY()
        return mouseX >= currentX &&
            mouseX <= currentX + max(size[0], 4.0f) &&
            mouseY >= currentY &&
            mouseY <= currentY + max(size[1], 4.0f)
    }

    fun setX(x: Float, screenCoordinates: Boolean) {
        relativeX = if (screenCoordinates) {
            x / ClientAccess.collectiveMinecraft.window.guiScaledWidth
        } else {
            Mth.clamp(x, 0.0f, 1.0f)
        }
    }

    fun setY(y: Float, screenCoordinates: Boolean) {
        relativeY = if (screenCoordinates) {
            y / ClientAccess.collectiveMinecraft.window.guiScaledHeight
        } else {
            Mth.clamp(y, 0.0f, 1.0f)
        }
    }

    fun getContentY(contentHeight: Float): Float {
        return if (verticalDirection == -1) {
            getY() + _dimensions[1] - contentHeight
        } else {
            getY()
        }
    }

    fun getContentX(contentWidth: Float): Float {
        return if (_anchor == HudAnchor.TOP_CENTER) {
            getX() + _dimensions[0] / 2.0f - contentWidth / 2.0f
        } else if (horizontalDirection == -1) {
            getX() + _dimensions[0] - contentWidth
        } else {
            getX()
        }
    }

    fun resetSnapping() {
        horizontalAlignment = HudHorizontalAlignment.FREE
        verticalAlignment = HudVerticalAlignment.FREE
    }

    fun updateDirections() {
        _horizontalDirection = if (getX() + _dimensions[0] / 2.0f > ClientAccess.collectiveMinecraft.window.guiScaledWidth / 2.0f) {
            -1
        } else {
            1
        }
        _verticalDirection = if (getY() + _dimensions[1] / 2.0f > ClientAccess.collectiveMinecraft.window.guiScaledHeight / 2.0f) {
            -1
        } else {
            1
        }
    }

    fun updateEnabledAnimation() {
        enabledAnimation.setTarget(if (isEnabled()) 1.0f else 0.0f, 250L)
    }

    companion object {
        private val editorBackgroundColor = Color(0, 0, 0, 120)

        @JvmField
        var renderingHud: Boolean = false
    }
}
