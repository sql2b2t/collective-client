package me.collective.client.gui.screens

import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.gui.GuiElement
import me.collective.client.gui.components.ActionButtonComponent
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.Module
import net.minecraft.client.gui.screens.Screen

class ModuleImportConfirmationScreen(
    private val returnScreen: Screen,
    private val module: Module,
    private val moduleJson: JsonObject
) : ClientScreen() {

    init {
        val confirmationPanel = Panel("Confirmation")
        val moduleLabel = ButtonComponent(confirmationPanel, "Module: " + module.name)
        val importButton = ActionButtonComponent(confirmationPanel, "Import") { this.importModule() }
        val cancelButton = ActionButtonComponent(confirmationPanel, "Cancel") { this.closeWithTransition() }
        val children: Array<GuiElement> = arrayOf(moduleLabel, importButton, cancelButton)
        confirmationPanel.addChildren(*children)
        this.panels.add(confirmationPanel)
    }

    private fun importModule() {
        this.module.fromJson(this.moduleJson)
        this.closeWithTransition()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 257) {
            this.importModule()
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun closeWithTransition() {
        if (this.returnScreen is ClientScreen) {
            this.returnScreen.open()
        } else {
            ClientAccess.collectiveMinecraft.setScreen(this.returnScreen)
        }
    }
}
