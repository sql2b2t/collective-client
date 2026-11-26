package me.collective.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.modules.client.UIModule
import me.collective.client.render.FontRenderer

interface GuiElement {
    val uiModule: UIModule
        get() = UIModule.INSTANCE

    val baseHeight: Int
        get() = UIModule.INSTANCE.styleSetting.value.heightPadding + FontRenderer.INSTANCE.getHeight()

    var yOffset: Int
        get() = 0
        set(yOffset) {}

    val textYOffset: Float
        get() = (baseHeight - FontRenderer.INSTANCE.getHeight() + 1) / 2.0f

    val height: Int
        get() = baseHeight

    fun keyPressed(keyCode: Int) {}

    fun init() {}

    fun render(stack: PoseStack, mouseX: Double, mouseY: Double) {}

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {}

    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = false

    fun update(mouseX: Double, mouseY: Double) {}

    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) {}

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {}

    fun charTyped(character: Char) {}
}

fun GuiElement.getUiModule(): UIModule = uiModule
fun GuiElement.getBaseHeight(): Int = baseHeight
fun GuiElement.getYOffset(): Int = yOffset
fun GuiElement.setYOffset(yOffset: Int) { this.yOffset = yOffset }
fun GuiElement.getTextYOffset(): Float = textYOffset
fun GuiElement.getHeight(): Int = height
