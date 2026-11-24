package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.client.HUDModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.FloatAnimation
import java.awt.Color
import kotlin.math.abs

class HudMarginController : ClientAccess {
    private var draggingFromRight: Boolean = false
    private var draggingFromBottom: Boolean = false
    private var draggingHorizontalMargin: Boolean = false
    private var draggingVerticalMargin: Boolean = false
    private val horizontalOverlayOpacity: FloatAnimation = FloatAnimation()
    private val verticalOverlayOpacity: FloatAnimation = FloatAnimation()

    private fun isOverHorizontalMargin(mouseX: Double): Boolean {
        val window = ClientAccess.collectiveMinecraft.window
        return mouseX < hud.safeX + 1 || mouseX > window.guiScaledWidth - hud.safeX - 1
    }

    private fun isOverVerticalMargin(mouseY: Double): Boolean {
        val window = ClientAccess.collectiveMinecraft.window
        return mouseY < hud.safeY + 1 || mouseY > window.guiScaledHeight - hud.safeY - 1
    }

    fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int) {
        val screenWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth
        val screenHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight
        val showHorizontalMargin = this.isOverHorizontalMargin(mouseX.toDouble()) || this.draggingHorizontalMargin
        val showVerticalMargin = this.isOverVerticalMargin(mouseY.toDouble()) || this.draggingVerticalMargin

        this.horizontalOverlayOpacity.setTarget(if (showHorizontalMargin) 140.0f else 0.0f, 250L)
        this.verticalOverlayOpacity.setTarget(
            if (showVerticalMargin && this.horizontalOverlayOpacity.value == 0.0f) 140.0f else 0.0f,
            250L
        )

        if (this.horizontalOverlayOpacity.value > 0.0f) {
            if (this.draggingHorizontalMargin) {
                hud.safeX = (if (this.draggingFromRight) abs(mouseX - screenWidth) else mouseX).toInt()
            }

            val horizontalOverlayColor = ColorUtils.toArgb(Color.BLACK, this.horizontalOverlayOpacity.value.toInt())
            GuiDrawUtils.fillRect(poseStack, 0.0f, 0.0f, hud.safeX.toFloat(), screenHeight.toFloat(), horizontalOverlayColor)
            GuiDrawUtils.fillRect(poseStack, (screenWidth - hud.safeX).toFloat(), 0.0f, screenWidth.toFloat(), screenHeight.toFloat(), horizontalOverlayColor)
        } else if (this.verticalOverlayOpacity.value > 0.0f) {
            if (this.draggingVerticalMargin) {
                hud.safeY = (if (this.draggingFromBottom) abs(mouseY - screenHeight) else mouseY).toInt()
            }

            val verticalOverlayColor = ColorUtils.toArgb(Color.BLACK, this.verticalOverlayOpacity.value.toInt())
            GuiDrawUtils.fillRect(poseStack, 0.0f, 0.0f, screenWidth.toFloat(), hud.safeY.toFloat(), verticalOverlayColor)
            GuiDrawUtils.fillRect(poseStack, 0.0f, (screenHeight - hud.safeY).toFloat(), screenWidth.toFloat(), screenHeight.toFloat(), verticalOverlayColor)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        if (button == 0) {
            this.draggingHorizontalMargin = this.isOverHorizontalMargin(mouseX)
            if (!this.draggingHorizontalMargin) {
                this.draggingVerticalMargin = this.isOverVerticalMargin(mouseY)
            }
            val window = ClientAccess.collectiveMinecraft.window
            this.draggingFromRight = mouseX > window.guiScaledWidth / 2.0f
            this.draggingFromBottom = mouseY > window.guiScaledHeight / 2.0f
        }
    }

    fun mouseReleased(button: Int) {
        if (button == 0) {
            this.draggingHorizontalMargin = false
            this.draggingVerticalMargin = false
        }
    }

    companion object {
        @JvmStatic
        private val hud: HUDModule get() = ClientContext.getModule(HUDModule::class.java) as HUDModule
    }
}
