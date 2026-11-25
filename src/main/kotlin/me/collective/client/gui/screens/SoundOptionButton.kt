package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.render.RenderUtils
import me.collective.client.utils.collections.NamedIdentifier

class SoundOptionButton(
    private val screen: SoundSelectionScreen,
    panel: Panel,
    private val soundId: NamedIdentifier
) : ButtonComponent(panel, soundId.name) {

    override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        if (this.screen.selectedSetting.value == this.soundId) {
            RenderUtils.INSTANCE.fillRect(
                poseStack,
                this.panel.x + 1f,
                this.panel.y + this.yOffset + 0.5f,
                this.panel.x + this.panel.width - 1f,
                this.panel.y + this.yOffset + this.height - 0.5f,
                this.uiModule.colorSetting.value
            )
        } else {
            this.renderButtonBackground(poseStack)
        }
        super.render(poseStack, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        if (this.hovered) {
            if (button == 0) {
                this.screen.selectedSetting.value = this.soundId
                this.screen.closeWithTransition()
            } else if (button == 1) {
                ClientContext.soundManager.playSound(this.soundId, 1.0f)
            }
        }
    }
}
