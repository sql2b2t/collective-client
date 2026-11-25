package me.collective.client.gui.screens

import me.collective.client.ClientAccess
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ShulkerBoxMenu

class ShulkerPreviewScreen(
    menu: ShulkerBoxMenu,
    inventory: Inventory,
    title: Component,
    private val parentScreen: Screen
) : ShulkerBoxScreen(menu, inventory, title), ClientAccess {

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderingPreview = true
        super.render(graphics, mouseX, mouseY, partialTick)
        renderingPreview = false
    }

    fun rejectMouseClick(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (keyCode == 256) {
            ClientAccess.collectiveMinecraft.setScreen(parentScreen)
            true
        } else {
            super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    companion object {
        @JvmStatic
        var renderingPreview: Boolean = false
            private set

        @JvmStatic
        fun isRenderingPreview(): Boolean = renderingPreview
    }
}
