package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.components.HudModulePanel
import me.collective.client.gui.components.ModuleComponent
import me.collective.client.gui.components.StringSettingComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.client.HUDModule
import me.collective.client.render.ClientRenderPipelines
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.GuiProjectionUtils
import me.collective.client.render.HudAnchorGroup
import me.collective.client.render.HudHoverLabel
import me.collective.client.render.HudWidgetRenderer
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.settings.options.HudAnchor
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.collections.IntPair
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.util.ArrayList
import java.util.Collections

open class HudEditorScreen : ClientScreen(), ClientAccess {
    private val marginController: HudMarginController = HudMarginController()
    private val hoverLabelOpacity: ClampedInterpolatedFloat = ClampedInterpolatedFloat(2.0f, true)
    private val modulePanel: HudModulePanel = HudModulePanel()
    private var draggingWidget: Boolean = false
    private var centerPanelPending: Boolean = false
    private var directionsRefreshPending: Boolean = false
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f
    private var mouseX: Float = 0f
    private var mouseY: Float = 0f

    init {
        panels.add(modulePanel)
        if (anchorGroups.isEmpty()) {
            for (anchor in HudAnchor.entries) {
                anchorGroups.add(HudAnchorGroup(anchor))
            }
        }
    }

    private fun getArrowKeyOffset(keyCode: Int): IntPair? {
        return when (keyCode) {
            262 -> IntPair(1, 0)
            263 -> IntPair(-1, 0)
            264 -> IntPair(0, 1)
            265 -> IntPair(0, -1)
            else -> null
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        marginController.mouseReleased(event.button())
        val released = super.mouseReleased(event)
        if (draggingWidget) {
            draggingWidget = false
            val freeGroup = getAnchorGroup(HudAnchor.NONE)
            val widget = freeGroup?.widgets?.lastOrNull() as? HudWidgetRenderer
            if (widget != null) {
                widget.isSelected = false
                freeGroup.widgets.remove(widget)
                val window = ClientAccess.collectiveMinecraft.window
                val screenWidth = window.guiScaledWidth
                val screenHeight = window.guiScaledHeight
                widget.updateSnapping(screenWidth.toFloat(), screenHeight.toFloat(), 5.0f)

                for (group in anchorGroups) {
                    for (widgetElement in group.widgets) {
                        val otherWidget = widgetElement as HudWidgetRenderer
                        if (otherWidget.bounds.intersects(widget.bounds)) {
                            getAnchorGroup(group.anchor)?.widgets?.add(widget)
                            widget.setAnchor(group.anchor)
                            return true
                        }
                    }
                }

                for (anchor in HudAnchor.entries) {
                    if (anchor.bounds.intersects(widget.bounds.expand(5.0f))) {
                        getAnchorGroup(anchor)?.widgets?.add(widget)
                        widget.setAnchor(anchor)
                        return true
                    }
                }
            }
        }

        return released
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        var dragAnchor: HudAnchor? = null
        var dragCandidate: HudWidgetRenderer? = null

        for (group in anchorGroups) {
            for (widgetIndex in group.widgets.size - 1 downTo 0) {
                val widget = group.widgets[widgetIndex] as HudWidgetRenderer
                if (widget.isEnabled
                    && dragCandidate == null
                    && widget.isMouseOver(event.x(), event.y())
                    && !(widget is ResizableHudWidgetRenderer && widget.isResizeHandleHovered(event.x(), event.y()))
                ) {
                    if (event.button() == 0) {
                        dragAnchor = group.anchor
                        dragCandidate = widget
                        dragOffsetX = (event.x() - widget.x).toInt().toFloat()
                        dragOffsetY = (event.y() - widget.y).toInt().toFloat()
                        draggingWidget = true
                        widget.isSelected = true
                        widget.resetSnapping()
                    } else if (event.button() == 1) {
                        val moduleRow = modulePanel.children
                            .filterIsInstance<ModuleComponent>()
                            .firstOrNull { it.module == widget.widgetModule }
                        moduleRow?.toggleOpen()
                    }
                }
            }
        }

        if (dragCandidate != null && dragAnchor != null) {
            getAnchorGroup(dragAnchor)?.widgets?.remove(dragCandidate)
            getAnchorGroup(HudAnchor.NONE)?.widgets?.add(dragCandidate)
            dragCandidate.setAnchor(HudAnchor.NONE)
        } else {
            marginController.mouseClicked(event.x(), event.y(), event.button())
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun snapWidgetAxis(
        poseStack: PoseStack, widget: HudWidgetRenderer, widgetSize: Float, screenSize: Float, margin: Float, horizontal: Boolean
    ) {
        val position = if (horizontal) widget.x else widget.y
        val screenCenter = screenSize / 2.0f
        val snappedPosition: Float
        if (Math.abs(position + widgetSize / 2.0f - screenCenter) <= 5.0f) {
            snappedPosition = screenCenter - widgetSize / 2.0f
            renderSnapGuide(poseStack, (snappedPosition - 0.5f).toDouble(), horizontal)
            renderSnapGuide(poseStack, (snappedPosition + widgetSize).toDouble(), horizontal)
        } else if (Math.abs(position - margin) <= 5.0f) {
            snappedPosition = margin
            renderSnapGuide(poseStack, (snappedPosition - 0.5f).toDouble(), horizontal)
        } else {
            if (Math.abs(position + widgetSize - (screenSize - margin)) > 5.0f) {
                return
            }
            snappedPosition = screenSize - margin - widgetSize
            renderSnapGuide(poseStack, (snappedPosition + widgetSize).toDouble(), horizontal)
        }

        if (horizontal) {
            widget.setX(snappedPosition, true)
        } else {
            widget.setY(snappedPosition, true)
        }
    }

    private fun snapWidget(
        graphics: GuiGraphics, poseStack: PoseStack, widget: HudWidgetRenderer, size: FloatArray
    ) {
        val screenWidth = graphics.guiWidth().toFloat()
        val screenHeight = graphics.guiHeight().toFloat()
        snapWidgetAxis(poseStack, widget, size[0], screenWidth, hud.getSafeX().toFloat(), true)
        snapWidgetAxis(poseStack, widget, size[1], screenHeight, hud.getSafeY().toFloat(), false)
    }

    private fun renderSnapGuide(poseStack: PoseStack, position: Double, vertical: Boolean) {
        val window = ClientAccess.collectiveMinecraft.window
        val screenWidth = window.guiScaledWidth.toFloat()
        val screenHeight = window.guiScaledHeight.toFloat()
        val coordinate = position.toFloat()
        if (vertical) {
            GuiDrawUtils.fillRect(poseStack, coordinate, 0.0f, coordinate + 0.5f, screenHeight, Color.YELLOW)
        } else {
            GuiDrawUtils.fillRect(poseStack, 0.0f, coordinate, screenWidth, coordinate + 0.5f, Color.YELLOW)
        }
    }

    private fun swapAnchorContents(firstAnchor: HudAnchor, secondAnchor: HudAnchor) {
        val firstGroup = getAnchorGroup(firstAnchor) ?: return
        val secondGroup = getAnchorGroup(secondAnchor) ?: return
        val firstWidgets = ArrayList(firstGroup.widgets)
        firstGroup.widgets.clear()

        for (secondWidget in secondGroup.widgets) {
            firstGroup.widgets.add(secondWidget)
        }

        secondGroup.widgets.clear()

        for (firstWidget in firstWidgets) {
            secondGroup.widgets.add(firstWidget)
        }
    }

    fun addWidget(anchor: HudAnchor, widget: HudWidgetRenderer) {
        getAnchorGroup(anchor)?.widgets?.add(widget)
    }

    fun addWidget(widget: HudWidgetRenderer) {
        addWidget(HudAnchor.NONE, widget)
    }

    fun getAnchorGroup(anchor: HudAnchor): HudAnchorGroup? {
        for (group in getAnchorGroups()) {
            if (group.anchor == anchor) {
                return group
            }
        }
        return null
    }

    fun renderWidgets(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        if (!hud.bloomSetting.value) {
            renderWidgetContents(poseStack, mouseX, mouseY)
            return
        }

        GuiDrawUtils.flush()
        MaskShaderRenderer.setParameters(
            hud.bloomRadiusSetting.value.toFloat(), hud.bloomStrengthSetting.value, 1.0f, 1.0f
        )
        val mainTarget = ClientAccess.collectiveMinecraft.mainRenderTarget
        try {
            MaskShaderRenderer.captureMask({
                renderWidgetContents(poseStack, mouseX, mouseY)
                GuiDrawUtils.flush()
            }, Color.BLACK)
            MaskShaderRenderer.renderEffect("glowy")
            RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET, ClientRenderPipelines.BLIT_PREMULTIPLIED_ALPHA)
        } finally {
            RenderTargetUtils.setMainRenderTarget(mainTarget)
        }
    }

    private fun renderWidgetContents(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        for (group in getAnchorGroups()) {
            group.render(poseStack, mouseX, mouseY)
        }

        if (directionsRefreshPending) {
            for (groupToRefresh in anchorGroups) {
                for (widgetElement in groupToRefresh.widgets) {
                    val widget = widgetElement as HudWidgetRenderer
                    widget.updateDirections()
                }
            }
            directionsRefreshPending = false
        }
    }

    fun removeWidget(widget: HudWidgetRenderer) {
        for (group in getAnchorGroups()) {
            if (group.widgets.remove(widget)) {
                return
            }
        }
    }

    override fun init() {
        super.init()
        centerPanelPending = true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        var overPanel = false
        for (panel in panels) {
            if (panel.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                overPanel = true
                break
            }
        }

        if (!overPanel) {
            val widget = findHoveredWidget()
            val offset = getArrowKeyOffset(event.key())
            if (offset != null && widget != null && widget.isEnabled) {
                widget.setX(widget.x + offset.first, true)
                widget.setY(widget.y + offset.second, true)
                widget.updateDirections()
            } else {
                when (event.key()) {
                    73 -> {
                        if (ClientContext.getClickGui().editingSetting !is StringSettingComponent) {
                            swapAnchorContents(HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT)
                            swapAnchorContents(HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT)
                            requestDirectionRefresh()
                        }
                    }
                    259, 261 -> {
                        if (widget != null && widget.isEnabled) {
                            widget.widgetModule.isToggled = false
                        }
                    }
                }
            }
        }

        return super.keyPressed(event)
    }

    override fun onClose() {
        super.onClose()
        if (draggingWidget) {
            val freeGroup = getAnchorGroup(HudAnchor.NONE)
            (freeGroup?.widgets?.lastOrNull() as? HudWidgetRenderer)?.isSelected = false
            draggingWidget = false
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val poseStack = GuiProjectionUtils.toPoseStack(graphics)
        RenderUtils.setRenderColor(1.0f, 1.0f, 1.0f, getTransitionOpacity())
        hoverLabel.isVisible = false
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        val screenCenterX = graphics.guiWidth() / 2.0f
        val screenCenterY = graphics.guiHeight() / 2.0f
        val guideColor = ColorUtils.withAlpha(
            Color.WHITE,
            (RenderUtils.getRenderColor()[3] * 0.4f).toInt()
        )
        GuiDrawUtils.fillRect(poseStack, screenCenterX - 0.5f, 0.0f, screenCenterX + 0.5f, screenCenterY * 2.0f, guideColor)
        GuiDrawUtils.fillRect(poseStack, 0.0f, screenCenterY - 0.5f, screenCenterX - 0.5f, screenCenterY + 0.5f, guideColor)
        GuiDrawUtils.fillRect(poseStack, screenCenterX + 0.5f, screenCenterY - 0.5f, screenCenterX * 2.0f, screenCenterY + 0.5f, guideColor)
        marginController.render(poseStack, mouseX, mouseY)
        renderWidgets(poseStack, mouseX.toDouble(), mouseY.toDouble())

        if (draggingWidget) {
            val freeGroup = getAnchorGroup(HudAnchor.NONE)
            val widget = freeGroup?.widgets?.lastOrNull() as? HudWidgetRenderer
            if (widget != null) {
                val size = widget.dimensions
                widget.setX(
                    Math.max(0.0f, Math.min(graphics.guiWidth() - size[0], mouseX - dragOffsetX)),
                    true
                )
                widget.setY(
                    Math.max(0.0f, Math.min(graphics.guiHeight() - size[1], mouseY - dragOffsetY)),
                    true
                )
                widget.updateDirections()
                snapWidget(graphics, poseStack, widget, size)
            }
        }

        val labelOpacity = hoverLabelOpacity.updateAndGet()
        hoverLabelOpacity.setTarget(hoverLabel.isVisible)
        if (hoverLabel.isVisible || labelOpacity > 0.1) {
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                hoverLabel.text,
                hoverLabel.x + 10.0f * (1.0f - labelOpacity) * hoverLabel.horizontalDirection,
                hoverLabel.y,
                ColorUtils.withAlpha(Color.WHITE, (labelOpacity * 255.0f).toInt())
            )
        }

        super.render(graphics, mouseX, mouseY, partialTick)
        if (centerPanelPending) {
            centerModulePanel()
            centerPanelPending = false
        }
    }

    override fun renderBackgroundOverlay(poseStack: PoseStack) {
    }

    fun requestDirectionRefresh() {
        directionsRefreshPending = true
    }

    private fun findHoveredWidget(): HudWidgetRenderer? {
        for (group in anchorGroups) {
            for (widgetElement in group.widgets) {
                val widget = widgetElement as HudWidgetRenderer
                if (widget.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                    return widget
                }
            }
        }
        return null
    }

    private fun centerModulePanel() {
        if (panels.isEmpty()) return
        val panel = panels[0]
        val panelWidth = panel.width
        val panelHeight = panel.height
        val window = ClientAccess.collectiveMinecraft.window
        val panelX = (window.guiScaledWidth / 2.0f - panelWidth * 1.5f).toInt()
        val panelY = ((window.guiScaledHeight - panelHeight) / 2.0f).toInt()
        panel.x = panelX
        panel.y = panelY
    }

    fun getAnchorGroups(): List<HudAnchorGroup> {
        return anchorGroups
    }

    companion object {
        private val hud: HUDModule get() = ClientContext.getModule(HUDModule::class.java) as HUDModule
        private val anchorGroups: MutableList<HudAnchorGroup> = Collections.synchronizedList(ArrayList())

        @JvmField
        val hoverLabel: HudHoverLabel = HudHoverLabel()
    }
}
