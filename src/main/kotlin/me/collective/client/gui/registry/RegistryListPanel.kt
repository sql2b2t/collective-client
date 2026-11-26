package me.collective.client.gui.registry

import me.collective.client.ClientAccess
import me.collective.client.gui.panels.Panel

open class RegistryListPanel(title: String) : Panel(title) {
    override fun getHeightLimit(): Int {
        return ClientAccess.collectiveMinecraft.window.guiScaledHeight / 2 + 10
    }
}
