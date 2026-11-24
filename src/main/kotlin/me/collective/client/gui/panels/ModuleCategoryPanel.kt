package me.collective.client.gui.panels

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.gui.GuiElement
import me.collective.client.gui.components.ModuleComponent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.FontRenderer
import me.collective.client.render.TextUtils
import net.minecraft.client.gui.components.EditBox

open class ModuleCategoryPanel(val category: ModuleCategory) : Panel(FontRenderer.capitalize(category.name)), ClientAccess {

    init {
        this.populateModules()
    }

    private fun populateModules() {
        val modules = (ClientContext.moduleRegistry.getRegistry() as Collection<Module>)
            .filter { it.category === this.category }
            .sortedBy { it.name }
        for (module in modules) {
            val component = ModuleComponent(module, this, 0)
            this.children.add(component)
        }
    }

    override fun init() {
        this.updateLayout()
        super.init()
    }

    override fun isChildVisible(child: GuiElement): Boolean {
        val searchBox: EditBox? = ClientContext.getClickGui().searchBox
        if (searchBox == null) {
            return true
        }

        return if (child is ModuleComponent && searchBox.value.isNotEmpty()) {
            for (alias in child.module.aliases) {
                if (TextUtils.containsIgnoreCase(alias, TextUtils.normalizeKeyboardLayout(searchBox.value))) {
                    return true
                }
            }
            false
        } else {
            true
        }
    }
}
