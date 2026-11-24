package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.CollectiveClient
import me.collective.client.gui.CursorType
import me.collective.client.gui.ScissorStack
import me.collective.client.gui.panels.ModuleCategoryPanel
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.client.UIModule
import me.collective.client.modules.render.BlurModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.GuiProjectionUtils
import me.collective.client.render.GuiScaleUtils
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.utils.world.Season
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.time.LocalDate
import java.util.ArrayList
import java.util.Comparator

open class ClientScreen(title: Component) : Screen(title), ClientAccess {

    constructor() : this(Component.literal(CollectiveClient.NAME))

    @JvmField
    val panels: ArrayList<Panel> = ArrayList()
    protected val seasonalParticles: MutableList<SnowflakeParticle> = ArrayList()
    protected val hoverMouseX: InterpolatedFloat = InterpolatedFloat(2.5f, true)
    protected val hoverMouseY: InterpolatedFloat = InterpolatedFloat(2.5f, true)

    @JvmField
    protected var panelDragging: Boolean = false

    @JvmField
    protected var guiScale: Float = 0.0f

    @JvmField
    protected var closing: Boolean = false

    @JvmField
    protected var tooltipText: String = ""

    @JvmField
    protected var lastHoverTime: Long = 0L

    @JvmField
    protected var transitionStartedAt: Long = 0L

    var parentScreen: Screen? = null
        @JvmName("getParentScreenProp") get
        @JvmName("setParentScreenProp") set

    fun getParentScreen(): Screen? = this.parentScreen
    fun setParentScreen(screen: Screen?) { this.parentScreen = screen }

    @JvmField
    protected var escapeCloseAllowed: Boolean = false

    private var skipOpeningTransition: Boolean = false

    init {
        this.resetSeasonalParticles()
    }

    open fun isClosing(): Boolean = this.closing

    open fun suppressEscapeClose() {
        this.escapeCloseAllowed = false
    }

    open fun markHovered() {
        this.lastHoverTime = System.currentTimeMillis()
    }

    open fun resetSeasonalParticles() {
        this.seasonalParticles.clear()
        for (particleIndex in 0 until MAX_SEASONAL_PARTICLES) {
            this.seasonalParticles.add(SnowflakeParticle())
        }
    }

    open fun getTransitionOpacity(): Float {
        var opacity = (System.currentTimeMillis() - this.transitionStartedAt).toFloat() / TRANSITION_DURATION_MILLIS
        if (this.closing) {
            opacity = 1.0f - opacity
        }
        return Mth.clamp(opacity, 0.0f, 1.0f)
    }

    open fun setPanelDragging(dragging: Boolean) {
        this.panelDragging = dragging
    }

    open fun isPanelDragging(): Boolean = this.panelDragging

    fun hasMenuParent(): Boolean {
        val parent = this.getParentScreen()
        return parent is TitleScreen || parent is DisconnectedScreen || parent is JoinMultiplayerScreen
    }

    open fun getGuiScale(): Float = this.guiScale

    override fun isPauseScreen(): Boolean = false

    protected open fun reset() {
        this.lastHoverTime = 0L
    }

    open fun onMouseClick(mouseX: Double, mouseY: Double, button: Int) {}

    open fun onMouseRelease(mouseX: Double, mouseY: Double, button: Int) {}

    open fun setTooltipText(text: String) {
        this.tooltipText = text
    }

    override fun tick() {
        if (this.closing && System.currentTimeMillis() - this.transitionStartedAt >= TRANSITION_DURATION_MILLIS) {
            for (panel in this.panels) {
                panel.setDragging(false)
            }

            if (this.parentScreen != null) {
                ClientAccess.collectiveMinecraft.setScreen(this.parentScreen)
            } else {
                this.onClose()
            }

            this.setParentScreen(null)
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale)
        val scaledEvent = MouseButtonEvent(
            event.x() / scale,
            event.y() / scale,
            event.buttonInfo()
        )

        for (panelIndex in this.panels.size - 1 downTo 0) {
            val panel = this.panels[panelIndex]
            panel.setDragging(false)
            panel.mouseReleased(scaledEvent.x(), scaledEvent.y(), scaledEvent.button())
        }

        this.onMouseRelease(scaledEvent.x(), scaledEvent.y(), scaledEvent.button())
        return super.mouseReleased(scaledEvent)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        for (panelIndex in this.panels.size - 1 downTo 0) {
            this.panels[panelIndex].charTyped(event.codepointAsString()[0])
        }

        return super.charTyped(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (this.closing) {
            return false
        }

        val scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale)
        val scaledEvent = MouseButtonEvent(
            event.x() / scale,
            event.y() / scale,
            event.buttonInfo()
        )
        val handledByScreen = super.mouseClicked(scaledEvent, doubleClick)
        var draggedPanel: Panel? = null

        for (panelIndex in this.panels.size - 1 downTo 0) {
            val panel = this.panels[panelIndex]
            if (panel.isMouseOverHeader(scaledEvent.x(), scaledEvent.y()) && scaledEvent.button() == 0 && draggedPanel == null) {
                draggedPanel = panel
                panel.setDragging(true)
                panel.dragOffsetX = (scaledEvent.x() - panel.getX()).toInt()
                panel.dragOffsetY = (scaledEvent.y() - panel.getY()).toInt()
            }

            panel.mouseClicked(scaledEvent.x(), scaledEvent.y(), scaledEvent.button())
        }

        if (draggedPanel != null) {
            this.panels.remove(draggedPanel)
            this.panels.add(draggedPanel)
        }

        this.reset()
        this.onMouseClick(scaledEvent.x(), scaledEvent.y(), scaledEvent.button())
        return handledByScreen
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale)
        val scaledMouseX = mouseX / scale
        val scaledMouseY = mouseY / scale
        if (verticalAmount != 0.0) {
            for (panel in this.panels) {
                if (!panel.isDragging()) {
                    panel.mouseScrolled(scaledMouseX, scaledMouseY, verticalAmount)
                }
            }
        }

        return super.mouseScrolled(scaledMouseX, scaledMouseY, horizontalAmount, verticalAmount)
    }

    open fun renderContents(
        graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float
    ) {
    }

    override fun init() {
        val window = ClientAccess.collectiveMinecraft.window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight
        this.getParentScreen()?.init(screenWidth, screenHeight)

        if (!this.isWorldUnavailable()) {
            this.hoverMouseX.setValue(screenWidth / 2.0f)
            this.hoverMouseY.setValue(screenHeight / 2.0f)
        }

        this.panelDragging = false
        this.getPanels().forEach { panel -> panel.setDragging(false) }
        this.guiScale = UIModule.INSTANCE.scaleSetting.getValue()
        this.closing = false

        for (panel in this.panels) {
            panel.init()
        }

        if (this.skipOpeningTransition) {
            this.transitionStartedAt = -TRANSITION_DURATION_MILLIS
            this.skipOpeningTransition = false
        } else {
            this.transitionStartedAt = System.currentTimeMillis()
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        this.escapeCloseAllowed = true

        for (panelIndex in this.panels.size - 1 downTo 0) {
            this.panels[panelIndex].keyPressed(event.key())
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.escapeCloseAllowed) {
            this.closeWithTransition()
        }

        return false
    }

    override fun onClose() {
        super.onClose()
        CursorType.STANDARD.apply()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val poseStack = GuiProjectionUtils.toPoseStack(graphics)
        this.hoverMouseX.setTarget(mouseX.toFloat())
        this.hoverMouseY.setTarget(mouseY.toFloat())
        var hoverX = this.hoverMouseX.updateAndGet()
        var hoverY = this.hoverMouseY.updateAndGet()
        poseStack.translate(0.0f, 0.0f, SCREEN_CONTENT_DEPTH)
        cursor.apply()
        cursor = CursorType.STANDARD
        this.seasonalParticles.removeIf { it.isExpired }
        if (this.seasonalParticles.size < MAX_SEASONAL_PARTICLES) {
            this.seasonalParticles.add(SnowflakeParticle())
        }

        RenderUtils.setRenderColor(1.0f, 1.0f, 1.0f, this.getTransitionOpacity())
        val scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale).toFloat()
        GuiScaleUtils.applyGuiScale(this.guiScale)
        poseStack.scale(scale, scale, 1.0f)
        val scaledMouseX = (mouseX / scale).toInt()
        val scaledMouseY = (mouseY / scale).toInt()
        hoverX /= scale
        hoverY /= scale
        this.tooltipText = ""
        this.renderBackgroundOverlay(poseStack)
        if (UI.aeroSetting.getValue()) {
            this.renderAeroPanels(poseStack)
        }

        val blurStrength = BLUR.radius * this.getTransitionOpacity()
        if (!this.isClosing() && BLUR.isToggled) {
            GuiDrawUtils.flush()
            MaskShaderRenderer.renderBlur(
                {
                    for (blurPanel in this.getPanels()) {
                        val expansion = blurPanel.getExpansionValue()
                        val expandedContentHeight = (blurPanel.height - blurPanel.getHeaderHeight()) * expansion
                        GuiDrawUtils.fillRect(
                            poseStack,
                            blurPanel.getX().toFloat(),
                            blurPanel.getY().toFloat(),
                            (blurPanel.getX() + blurPanel.getWidth()).toFloat(),
                            blurPanel.getY() + blurPanel.getHeaderHeight() + expandedContentHeight,
                            -1
                        )
                    }
                    GuiDrawUtils.flush()
                },
                blurStrength
            )
        }

        if (Season.WINTER.containsMonth(LocalDate.now().monthValue)) {
            for (particle in this.seasonalParticles) {
                particle.render(poseStack)
            }
        }

        for (panel in this.panels) {
            panel.updateDragPosition(scaledMouseX, scaledMouseY)
            panel.render(poseStack, scaledMouseX.toDouble(), scaledMouseY.toDouble())
        }

        if (this.tooltipText.isNotBlank()) {
            GuiDrawUtils.flush()
            val tooltipLines = this.tooltipText.split("\n")
            var tooltipWidth = 0.0f

            for (tooltipLine in tooltipLines) {
                val lineWidth = FontRenderer.INSTANCE.getWidth(tooltipLine)
                if (lineWidth > tooltipWidth) {
                    tooltipWidth = lineWidth
                }
            }

            var tooltipLeft = hoverX + 9.0f
            var tooltipRight = hoverX + tooltipWidth + 10.0f
            if (tooltipRight > GuiScaleUtils.getScaledWidth()) {
                tooltipLeft = hoverX - tooltipWidth - 2.0f
                tooltipRight = hoverX - 1.0f
            }

            val blurLeft = tooltipLeft
            val blurRight = tooltipRight
            val blurY = hoverY.toInt()
            val tooltipBottom = hoverY - 1.0f + (FontRenderer.INSTANCE.getHeight() + 1.0f) * tooltipLines.size
            MaskShaderRenderer.renderBlur(
                {
                    RenderUtils.INSTANCE.fillRect(
                        poseStack,
                        blurLeft,
                        blurY - 1.0f,
                        blurRight,
                        tooltipBottom,
                        ColorUtils.packArgb(10, 10, 10, 140)
                    )
                },
                6.0f
            )
            poseStack.pushPose()
            poseStack.translate(0.0f, 0.0f, TOOLTIP_BACKGROUND_DEPTH)
            RenderUtils.INSTANCE.fillRect(
                poseStack,
                tooltipLeft,
                hoverY - 1.0f,
                tooltipRight,
                tooltipBottom,
                Color(10, 10, 10, 80)
            )
            var lineIndex = 0

            for (tooltipLine in tooltipLines) {
                FontRenderer.INSTANCE.drawWithShadow(
                    poseStack,
                    tooltipLine,
                    tooltipLeft + 1.0f,
                    hoverY + (FontRenderer.INSTANCE.getHeight() + 1.0f) * lineIndex,
                    Color.white
                )
                lineIndex++
            }

            this.lastHoverTime = System.currentTimeMillis()
            poseStack.popPose()
        } else if (this.lastHoverTime != 0L && System.currentTimeMillis() > this.lastHoverTime + 750L) {
            this.lastHoverTime = 0L
        }

        this.renderContents(graphics, poseStack, scaledMouseX, scaledMouseY, partialTick)
        GuiDrawUtils.flush()
        RenderUtils.setRenderColor(1.0f, 1.0f, 1.0f, 1.0f)
        GuiScaleUtils.restoreGuiScale()
        poseStack.translate(0.0f, 0.0f, -SCREEN_CONTENT_DEPTH)
    }

    open fun open() {
        val currentScreen = ClientAccess.collectiveMinecraft.screen
        if (currentScreen is ClientScreen) {
            this.setParentScreen(currentScreen.parentScreen)
            currentScreen.setParentScreen(null)
            this.skipOpeningTransition = true
        } else {
            this.setParentScreen(currentScreen)
        }

        ClientAccess.collectiveMinecraft.setScreen(this)
    }

    open fun closeWithTransition() {
        if (!this.closing) {
            this.panelDragging = false
            this.transitionStartedAt = System.currentTimeMillis()
            if (UIModule.INSTANCE.isToggled) {
                UIModule.INSTANCE.toggle()
            }
        } else {
            this.onClose()
        }

        this.closing = true
    }

    open fun resetPanelPositions() {
        this.resetSeasonalParticles()
        this.panels.sortWith(
            Comparator.comparing { panelEntry ->
                if (panelEntry is ModuleCategoryPanel) panelEntry.category.ordinal else 100
            }
        )
        var panelX = PANEL_START_X

        for (panel in this.panels) {
            panel.setX(panelX)
            panel.setY(PANEL_START_Y)
            panelX += panel.getWidth() + PANEL_GAP
        }
    }

    open fun renderAeroPanels(poseStack: PoseStack) {
        val textureAlpha = RenderUtils.getRenderColor()[3] / 2
        val textureColor = ARGB.color(textureAlpha, -1)

        for (panel in this.getPanels()) {
            val outlinePadding = 0
            val panelX = panel.getX()
            val panelY = panel.getY()
            val expansion = panel.getExpansionValue()
            var contentHeight = (panel.height - panel.getHeaderHeight()).toFloat()
            val panelWidth = panel.getWidth().toFloat()

            contentHeight *= expansion
            poseStack.pushPose()
            poseStack.translate(panelX.toFloat(), panelY.toFloat(), 0.0f)
            ScissorStack.push(
                panelX,
                panelY,
                panelWidth.toInt(),
                Math.floor((panel.getHeaderHeight() + contentHeight + outlinePadding).toDouble()).toInt()
            )
            GuiDrawUtils.drawTexture(
                poseStack,
                AERO_TEXTURE,
                (-300.0f + panelX / 3.0f).toInt(),
                0,
                0.0f,
                0.0f,
                600,
                450,
                600,
                450,
                textureColor
            )
            RenderUtils.getEntityBuffers().endBatch()
            ScissorStack.pop()
            poseStack.popPose()
        }
    }

    open fun renderBackgroundOverlay(poseStack: PoseStack) {
        GuiDrawUtils.fillRect(
            poseStack,
            0.0f,
            0.0f,
            GuiScaleUtils.getScaledWidth().toFloat(),
            GuiScaleUtils.getScaledHeight().toFloat(),
            UIModule.INSTANCE.tintSetting.getValue()
        )
        GuiDrawUtils.fillVerticalGradient(
            poseStack,
            0.0f,
            0.0f,
            GuiScaleUtils.getScaledWidth().toFloat(),
            GuiScaleUtils.getScaledHeight().toFloat(),
            ColorUtils.toArgb(UIModule.INSTANCE.gradientColorSetting.getValue(), 0),
            UIModule.INSTANCE.gradientColorSetting.getValue().hashCode()
        )
        GuiDrawUtils.flush(false)
    }

    companion object {
        private val AERO_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("collective", "textures/aero.png")
        private val BLUR: BlurModule
            get() = ClientContext.getModule(BlurModule::class.java) as BlurModule
        private val UI: UIModule
            get() = ClientContext.getModule(UIModule::class.java) as UIModule

        private const val TRANSITION_DURATION_MILLIS: Long = 150L
        private const val MAX_SEASONAL_PARTICLES: Int = 25
        private const val PANEL_START_X: Int = 10
        private const val PANEL_START_Y: Int = 5
        private const val PANEL_GAP: Int = 3
        private const val TOOLTIP_BACKGROUND_DEPTH: Float = 500.0f
        private const val SCREEN_CONTENT_DEPTH: Float = 1000.0f

        @JvmField
        var cursor: CursorType = CursorType.STANDARD
    }

    fun getPanels(): ArrayList<Panel> = this.panels
}
