package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.FloatAnimation
import me.collective.client.render.ColorUtils
import me.collective.client.managers.CommandRegistry
import me.collective.client.settings.Setting

import java.awt.Color
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen

class ChatInputOverlay(private val hudModule: HUDModule) : ClientAccess {
    private val commandHighlightAnimation = FloatAnimation()

    @EventHandler
    private fun onHudRender(event: HudRenderEvent.Unconditional) {
        val commandInput = isCommandInput()
        commandHighlightAnimation.setTarget(commandInput, 250L)
        val animationValue = commandHighlightAnimation.getValue()
        val highlightAlpha = animationValue * 1.5f
        val chatInputHeight = this.hudModule.chatInputHeight.getValue()
        val screenWidth = ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat()
        val screenHeight = ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat()
        val options = ClientAccess.collectiveMinecraft.options
        val backgroundArgb = options.getBackgroundColor(-2147483648)
        val backgroundColor = ColorUtils.multiplyAlpha(
            Color(backgroundArgb, true), chatInputHeight / 12.0f
        )
        val backgroundPose = event.poseStack!!
        val backgroundTop = screenHeight - 2.0f - chatInputHeight
        val backgroundRight = screenWidth - 2.0f
        GuiDrawUtils.fillRect(
            backgroundPose, 2.0f, backgroundTop, backgroundRight, screenHeight - 2.0f, backgroundColor
        )
        if (highlightAlpha > 0.0f) {
            val outlineColorSetting: Setting<Color> = UIModule.INSTANCE.colorSetting
            val outlineColor = ColorUtils.multiplyAlpha(
                outlineColorSetting.value, highlightAlpha
            )
            val outlinePose = event.poseStack!!
            val outlineTop = screenHeight - 3.0f - chatInputHeight
            val outlineRight = screenWidth - 2.0f
            GuiDrawUtils.drawRectOutline(
                outlinePose, 1.0f, outlineTop, outlineRight, screenHeight - 2.0f, outlineColor
            )
        }
    }

    companion object {
        private fun isCommandInput(): Boolean {
            val screen = ClientAccess.collectiveMinecraft.screen
            if (screen is ChatScreen) {
                val input: EditBox = screen.input
                val inputText = input.value
                val prefix = CommandRegistry.getPrefix()
                return inputText.startsWith(prefix)
            } else {
                return false
            }
        }
    }
}
