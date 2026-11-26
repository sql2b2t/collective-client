package me.collective.client.gui.panels

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.GuiElement
import me.collective.client.gui.ScissorStack
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.modules.client.UIModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.GuiScaleUtils
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.utils.inventory.RegistryContainer
import net.minecraft.util.Mth
import java.awt.Color
import java.util.ArrayList

open class Panel(name: String) : RegistryContainer, GuiElement, NamedEntry {
    @JvmField
    val children: ArrayList<GuiElement> = ArrayList()

    @JvmField
    protected val expansionAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat({ 2.0f }, true)

    private val scrollAnimation: InterpolatedFloat = InterpolatedFloat(
        { this.uiModule.animSpeedSetting.getValue() * 2.0f },
        true
    )

    private val topScrollFadeAnimation: FloatAnimation = FloatAnimation()
    private val bottomScrollFadeAnimation: FloatAnimation = FloatAnimation()
    private val headerAlphaAnimation: FloatAnimation = FloatAnimation()
    @get:JvmName("panelName")
    val name: String = name

    override fun getName(): String = this.name

    @JvmField
    protected val baseWidth: Int = 92

    @JvmField
    var dragOffsetX: Int = 0

    @JvmField
    var dragOffsetY: Int = 0

    @JvmField
    protected var expanded: Boolean = true

    open var isExpanded: Boolean
        get() = this.expanded
        set(value) {
            this.expanded = value
        }

    @JvmField var y: Int = 5
    @JvmField var x: Int = 5

    fun getX(): Int = this.x
    fun setX(x: Int) { this.x = x }
    fun getY(): Int = this.y
    fun setY(y: Int) { this.y = y }
    fun getChildren(): ArrayList<GuiElement> = this.children

    val width: Int
        @JvmName("getWidthProp") get() = getWidth()

    val headerHeight: Int
        @JvmName("getHeaderHeightProp") get() = getHeaderHeight()

    val isFullyExpanded: Boolean
        @JvmName("isFullyExpandedProp") get() = isFullyExpanded()

    @JvmField
    protected var dragging: Boolean = false

    var isDragging: Boolean
        @JvmName("isDraggingProp") get() = isDragging()
        @JvmName("setDraggingProp") set(value) = setDragging(value)

    private var _height: Int = 0
    private var contentHeight: Int = 0
    private var heightLimitDisabled: Boolean = false
    private var scrollOffset: Int = 0
    private var scrollRecoveryCounter: Int = 0

    init {
        this.expansionAnimation.setValue(1.0f)
        this.headerAlphaAnimation.setValue(1.0f)
    }

    open fun isDragging(): Boolean = this.dragging

    open fun setDragging(requestedDragging: Boolean) {
        val clickGui = ClientContext.getClickGui()
        if (requestedDragging && !clickGui.isPanelDragging()) {
            clickGui.setPanelDragging(true)
            this.dragging = true
        } else {
            if (clickGui.isPanelDragging()) {
                clickGui.setPanelDragging(false)
            }
            this.dragging = false
        }
    }

    override val height: Int
        get() = this._height

    open fun getWidth(): Int {
        return this.baseWidth + UIModule.INSTANCE.styleSetting.getValue().getWidthPadding()
    }

    open fun getHeaderHeight(): Int {
        return if (this.uiModule.styleSetting.getValue() == UIModule.StyleChoice.SLIM) {
            12
        } else {
            14
        }
    }

    open fun getExpansionValue(): Float {
        return this.expansionAnimation.getValue()
    }

    fun isFullyExpanded(): Boolean {
        return this.expanded && this.expansionAnimation.currentValue >= 1.0f
    }

    fun disableHeightLimit() {
        this.heightLimitDisabled = true
    }

    protected open fun getHeightLimit(): Int {
        var guiScale = 1.0f
        val screen = ClientAccess.collectiveMinecraft.gui.screen()
        if (screen is ClientScreen) {
            guiScale = screen.getGuiScale()
        }

        return ((ClientAccess.collectiveMinecraft.window.guiScaledHeight - 50) / guiScale).toInt()
    }

    open fun updateLayout() {
        val animatedScrollOffset = this.scrollAnimation.getValue().toInt()
        var nextYOffset = this.getHeaderHeight() + 1 + animatedScrollOffset

        for (child in this.children) {
            if (!this.isChildVisible(child)) {
                child.yOffset = 0
            } else {
                child.yOffset = nextYOffset
                nextYOffset += child.height
            }
        }

        val heightLimit = this.getHeightLimit()
        this.contentHeight = if (this.expansionAnimation.getValue() == 0.0f) {
            this.getHeaderHeight() + 1
        } else {
            nextYOffset - animatedScrollOffset
        }

        if (this.heightLimitDisabled) {
            this._height = this.contentHeight
        } else {
            this._height = Math.min(this.contentHeight, heightLimit)
        }
    }

    private fun recoverScrollOffset() {
        if (!this.heightLimitDisabled) {
            val minimumOffset = Math.min(-this.contentHeight + this._height, 0)
            val maximumOffset = 0
            val framesPerSecond = ClientContext.frameTiming.getFramesPerSecond()
            var recoveryDivisor = framesPerSecond / SCROLL_RECOVERY_FPS_BASE * SCROLL_RECOVERY_SPEED
            if (framesPerSecond <= SLOW_FRAME_RATE) {
                recoveryDivisor /= 2.0f
            }

            if (this.scrollOffset < minimumOffset) {
                this.scrollOffset += Math.min(
                    this.scrollRecoveryCounter++ / recoveryDivisor,
                    (minimumOffset - this.scrollOffset).toFloat()
                ).toInt()
            } else if (this.scrollOffset > maximumOffset) {
                this.scrollOffset -= Math.min(
                    this.scrollRecoveryCounter++ / recoveryDivisor,
                    (this.scrollOffset - maximumOffset).toFloat()
                ).toInt()
            } else {
                this.scrollRecoveryCounter = 0
            }

            this.scrollOffset = Mth.clamp(
                this.scrollOffset,
                minimumOffset - MAX_SCROLL_OVERSHOOT,
                maximumOffset + MAX_SCROLL_OVERSHOOT
            )
        }
    }

    open fun updateDragPosition(mouseX: Int, mouseY: Int) {
        if (this.dragging) {
            val guiScale = getDragScale()
            val window = ClientAccess.collectiveMinecraft.window
            val maxX = (window.guiScaledWidth / guiScale - this.getWidth()).toInt()
            val maxY = (window.guiScaledHeight / guiScale - this._height).toInt()
            this.setX((mouseX - this.dragOffsetX).coerceIn(0, Math.max(0, maxX)))
            this.setY((mouseY - this.dragOffsetY).coerceIn(0, Math.max(0, maxY)))
        }
    }

    protected open fun isChildVisible(child: GuiElement): Boolean = true

    fun addChild(child: GuiElement) {
        this.children.add(child)
    }

    fun addChildren(vararg children: GuiElement) {
        for (child in children) {
            this.addChild(child)
        }
    }

    fun registerChild(child: GuiElement): Boolean {
        return this.children.add(child)
    }

    override fun register(entry: Any): Boolean {
        return this.registerChild(entry as GuiElement)
    }

    override fun unregister(entry: Any): Boolean {
        val element = entry as GuiElement
        return this.children.remove(element)
    }

    override fun getRegistry(): Collection<GuiElement> = this.children

    override fun init() {
        if (this.expanded) {
            for (child in this.children) {
                child.init()
            }
        }
    }

    override fun keyPressed(keyCode: Int) {
        if (this.expanded) {
            for (child in this.children) {
                child.keyPressed(keyCode)
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        if (this.isMouseOverHeader(mouseX, mouseY) && button == 1) {
            this.isExpanded = !this.expanded
        }

        if (this.expanded) {
            for (child in this.children) {
                child.mouseClicked(mouseX, mouseY, button)
            }
        }
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        if (this.expanded) {
            for (child in this.children) {
                child.mouseReleased(mouseX, mouseY, button)
            }
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollAmount: Double) {
        if (this.expanded) {
            if (this.isMouseOver(mouseX, mouseY) && !this.heightLimitDisabled) {
                this.scrollOffset += (scrollAmount * 20.0).toInt()
            }

            for (child in this.children) {
                child.mouseScrolled(mouseX, mouseY, scrollAmount)
            }
        }
    }

    override fun charTyped(character: Char) {
        if (this.expanded) {
            for (child in this.children) {
                child.charTyped(character)
            }
        }
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= this.x &&
            mouseX <= this.x + this.getWidth() &&
            mouseY >= this.y &&
            mouseY <= this.y + this._height
    }

    open fun isMouseOverHeader(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= this.x &&
            mouseX <= this.x + this.getWidth() &&
            mouseY >= this.y &&
            mouseY <= this.y + this.getHeaderHeight()
    }

    protected open fun applyHeaderAlpha(color: Color): Color {
        val alphaFactor = this.headerAlphaAnimation.getValue() * 0.5f + 0.5f
        return ColorUtils.withAlpha(color, color.alpha / 255.0f * alphaFactor)
    }

    private fun renderScrollFade(poseStack: PoseStack, fadeAmount: Float, top: Boolean) {
        var currentFade = fadeAmount
        val expansionValue = this.getExpansionValue()
        if (expansionValue < 1.0f) {
            currentFade *= expansionValue
        }

        val uiColor = UIModule.INSTANCE.colorSetting.getValue()
        var startColor = ColorUtils.toArgb(uiColor, 0)
        var endColor = ColorUtils.toArgb(uiColor, 0.3f * currentFade)
        if (top) {
            startColor = endColor
            endColor = ColorUtils.toArgb(uiColor, 0)
        }

        if (currentFade != 0.0f) {
            GuiDrawUtils.fillVerticalGradient(
                poseStack,
                this.x.toFloat(),
                if (top) {
                    (this.y + this.getHeaderHeight()).toFloat()
                } else {
                    this.y + this._height * expansionValue - 10.0f
                },
                this.getWidth().toFloat(),
                10.5f,
                startColor,
                endColor
            )
        }
    }

    override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        val uiColor = this.uiModule.colorSetting.getValue()
        this.expansionAnimation.setTarget(if (this.expanded) 1.0f else 0.0f)
        val expansionValue = this.expansionAnimation.updateAndGet()
        this.scrollAnimation.setTarget(this.scrollOffset.toFloat())
        this.scrollAnimation.updateAndGet()
        this.headerAlphaAnimation.setTarget(
            expansionValue > 0.25f || this.isMouseOverHeader(mouseX, mouseY),
            200L
        )
        val headerVerticalOffset = 0.5f
        GuiDrawUtils.fillRect(
            poseStack,
            this.x.toFloat(),
            this.y.toFloat(),
            (this.x + this.getWidth()).toFloat(),
            this.y + this.getHeaderHeight() + headerVerticalOffset,
            this.applyHeaderAlpha(uiColor)
        )
        FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            this.name,
            (this.x + 2).toFloat(),
            this.y + this.getHeaderHeight() / 2.0f - FontRenderer.INSTANCE.getHeight() / 2.0f + headerVerticalOffset,
            this.applyHeaderAlpha(Color.white)
        )
        if (expansionValue != 0.0f) {
            GuiDrawUtils.flush()
            ScissorStack.push(
                this.x - 1,
                this.y + this.getHeaderHeight(),
                this.getWidth() + 1,
                (this._height * expansionValue).toInt() + 1 - this.getHeaderHeight()
            )
        }

        val backgroundColor = UIModule.INSTANCE.backgroundSetting.getValue()
        if (expansionValue > 0.0f && this.children.isNotEmpty()) {
            GuiDrawUtils.fillRect(
                poseStack,
                this.x.toFloat(),
                (this.y + this.getHeaderHeight()).toFloat(),
                (this.x + this.getWidth()).toFloat(),
                this.y + this._height + 0.5f,
                backgroundColor
            )
            for (child in this.children) {
                if (this.isChildVisible(child)) {
                    child.update(mouseX, mouseY)
                    val childYOffset = child.yOffset
                    if (childYOffset <= this._height && childYOffset + child.height >= 0) {
                        child.render(poseStack, mouseX, mouseY)
                    }
                }
            }
        }

        if (expansionValue != 0.0f) {
            GuiDrawUtils.flush()
            ScissorStack.pop()
        }

        poseStack.pushPose()
        poseStack.translate(0.0f, 0.0f, 500.0f)
        this.bottomScrollFadeAnimation.setTarget(
            this.scrollOffset > Math.min(-this.contentHeight + this._height, 0),
            200L
        )
        this.topScrollFadeAnimation.setTarget(
            this.scrollOffset < 0,
            200L
        )
        this.renderScrollFade(
            poseStack,
            this.bottomScrollFadeAnimation.getValue(),
            false
        )
        this.renderScrollFade(
            poseStack,
            this.topScrollFadeAnimation.getValue(),
            true
        )
        poseStack.popPose()
        this.recoverScrollOffset()
        this.updateLayout()
        GuiDrawUtils.flush()
    }

    companion object {
        private const val SCROLL_RECOVERY_FPS_BASE = 200.0f
        private const val SCROLL_RECOVERY_SPEED = 20.0f
        private const val SLOW_FRAME_RATE = 40
        private const val MAX_SCROLL_OVERSHOOT = 40

        @JvmStatic
        fun getDragScale(): Double = GuiScaleUtils.getScaleMultiplier()
    }
}
