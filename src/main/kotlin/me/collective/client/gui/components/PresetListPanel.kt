package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.config.PresetData
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.PresetScreen
import java.util.function.Consumer

class PresetListPanel(private val screen: PresetScreen) : Panel("List") {
   private var layoutUpdatePending = false

   override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.layoutUpdatePending) {
         for (child in this.children) {
            child.update(mouseX, mouseY)
         }
         this.updateLayout()
         this.layoutUpdatePending = false
      }
      super.render(poses, mouseX, mouseY)
   }

   fun refreshPresets() {
      this.layoutUpdatePending = true
      this.children.clear()
      ClientContext.configurationManager
         .getPresetRegistry(this.screen.selectedCategory)
         .forEachPreset(
            Consumer { preset: PresetData ->
               this.addChild(PresetButton(this.screen, this, preset))
            }
         )
   }

   private class PresetButton(
      private val screen: PresetScreen,
      panel: Panel,
      private val preset: PresetData
   ) : ActionButtonComponent(
      panel,
      preset.name,
      Runnable {
         screen.presetPanel.editPreset(preset)
         screen.presetPanel.updateLayout()
      }
   ) {
      override fun isHighlighted(): Boolean {
         return this.preset == this.screen.presetPanel.getSelectedPreset()
      }
   }
}
