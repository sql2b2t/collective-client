package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.GuiElement
import me.collective.client.modules.client.HUDModule
import me.collective.client.settings.options.HudAnchor
import net.minecraft.util.Mth

class HudAnchorGroup(val anchor: HudAnchor) : ClientAccess, GuiElement {

    val widgets: MutableList<HudWidgetRenderer> = ArrayList()

    private fun prepareWidgetRender(enabledProgress: Float) {
        if (!HudWidgetRenderer.renderingHud && enabledProgress != 1.0f) {
            GuiDrawUtils.flush()
        }
    }

    private fun getBottomMargin(rightAligned: Boolean): Float {
        val hud = hud() ?: return 0.0f
        val sideOffset: Float = if (rightAligned) hud.getBottomHudOffset() else hud.getChatInputOffset()
        return Mth.clamp(hud.getSafeY().toFloat() + sideOffset, 0.0f, 16.0f)
    }

    private fun calculateY(
        height: Float,
        horizontalAlignment: HudHorizontalAlignment,
        verticalAlignment: HudVerticalAlignment
    ): Float {
        val hud = hud() ?: return 0.0f
        return when (verticalAlignment) {
            HudVerticalAlignment.TOP -> {
                val offset: Float = if (horizontalAlignment == HudHorizontalAlignment.RIGHT) {
                    hud.getEffectIconOffset()
                } else {
                    0.0f
                }
                hud.getSafeY().toFloat() + offset
            }
            HudVerticalAlignment.BOTTOM -> ClientAccess.collectiveMinecraft.window.guiScaledHeight -
                height - getBottomMargin(horizontalAlignment == HudHorizontalAlignment.RIGHT)
            HudVerticalAlignment.CENTER -> (ClientAccess.collectiveMinecraft.window.guiScaledHeight - height) / 2.0f
            else -> throw IllegalStateException("Unexpected value: $verticalAlignment")
        }
    }

    override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        poseStack.pushPose()
        if (anchor != HudAnchor.NONE) {
            applyHudTranslation(poseStack)
        }

        for (widgetIndex in widgets.indices) {
            val widget = widgets[widgetIndex]
            widget.updateEnabledAnimation()
            val enabledProgress: Float = widget.enabledProgress
            val visible = widget.isEnabled() || enabledProgress > 0.0f
            val size = if (!visible) FloatArray(2) else widget.calculateDimensions()

            widget.setDimensions(size)
            if (anchor != HudAnchor.NONE) {
                layoutAnchoredWidget(widget, size, widgetIndex)
            } else {
                layoutFreeWidget(widget, size)
            }

            prepareWidgetRender(enabledProgress)
            if (visible) {
                RenderUtils.setRenderColor(1.0f, 1.0f, 1.0f, enabledProgress)
                widget.render(poseStack, mouseX, mouseY)
                prepareWidgetRender(enabledProgress)
                if (!HudWidgetRenderer.renderingHud) {
                    RenderUtils.getEntityBuffers().endBatch(ClientRenderTypes.guiTriangles())
                }
                RenderUtils.setRenderColor(1.0f, 1.0f, 1.0f, 1.0f)
            }

            if (widgetIndex < widgets.size - 1 &&
                anchor != HudAnchor.NONE &&
                anchor.verticalAlignment != HudVerticalAlignment.BOTTOM
            ) {
                widgets[widgetIndex + 1].setY(widget.getY() + size[1], true)
            }
        }

        poseStack.popPose()
    }

    private fun layoutFreeWidget(widget: HudWidgetRenderer, size: FloatArray) {
        val horizontalAlignment = widget.horizontalAlignment
        val verticalAlignment = widget.verticalAlignment
        if (horizontalAlignment != HudHorizontalAlignment.FREE) {
            widget.setX(calculateX(size[0], horizontalAlignment), true)
        }
        if (verticalAlignment != HudVerticalAlignment.FREE) {
            widget.setY(calculateY(size[1], horizontalAlignment, verticalAlignment), true)
        }
    }

    private fun layoutAnchoredWidget(widget: HudWidgetRenderer, size: FloatArray, widgetIndex: Int) {
        widget.setX(calculateX(size[0], anchor.horizontalAlignment), true)
        if (widgetIndex == 0) {
            widget.setY(
                calculateY(size[1], anchor.horizontalAlignment, anchor.verticalAlignment),
                true
            )
        } else if (anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT) {
            widget.setY(widgets[widgetIndex - 1].getY() - size[1], true)
        }
    }

    private fun calculateX(width: Float, horizontalAlignment: HudHorizontalAlignment): Float {
        val hud = hud() ?: return 0.0f
        return when (horizontalAlignment) {
            HudHorizontalAlignment.LEFT -> hud.getSafeX().toFloat()
            HudHorizontalAlignment.RIGHT -> ClientAccess.collectiveMinecraft.window.guiScaledWidth - width - hud.getSafeX().toFloat()
            HudHorizontalAlignment.CENTER -> ClientAccess.collectiveMinecraft.window.guiScaledWidth / 2.0f - width / 2.0f
            else -> throw IllegalStateException("Unexpected value: $horizontalAlignment")
        }
    }

    private fun applyHudTranslation(poseStack: PoseStack) {
        val hud = hud() ?: return
        poseStack.translate(hud.getParallaxOffset())
    }

    companion object {
        private var hud: HUDModule? = null

        private fun hud(): HUDModule? {
            if (hud == null) {
                hud = ClientContext.getModuleOrNull(HUDModule::class.java)
            }
            return hud
        }
    }
}
