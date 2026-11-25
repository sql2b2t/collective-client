package me.collective.client.gui.components

import me.collective.client.gui.GuiElement
import me.collective.client.gui.panels.ModuleCategoryPanel
import me.collective.client.modules.ModuleCategory

class HudModulePanel : ModuleCategoryPanel(ModuleCategory.HUD) {

   override fun getHeightLimit(): Int {
      var height = 0
      for (childElement in this.children) {
         val child = childElement as GuiElement
         height += child.baseHeight + 1
      }
      return height
   }
}
