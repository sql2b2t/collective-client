package me.collective.client.gui.components

import me.collective.client.ClientAccess
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.TradeEditorScreen
import me.collective.client.gui.screens.TradeManagerScreen
import me.collective.client.settings.StringSetting

class CreateTradePanel(private val managerScreen: TradeManagerScreen) : Panel("Trade"), ClientAccess {
   private val nameSetting = StringSetting("Name", "")

   init {
      this.setX(this.getX() + this.getWidth() + 4)
      this.registerChild(StringSettingComponent(this, CloseState.ALWAYS_OPEN, this.nameSetting))
      this.registerChild(ActionButtonComponent(this, "Create", Runnable { this.createTrade() }))
   }

   private fun createTrade() {
      if (this.nameSetting.value.isNotBlank()) {
         val editorScreen = TradeEditorScreen(this.nameSetting.value)
         editorScreen.setParentScreen(this.managerScreen)
         ClientAccess.collectiveMinecraft.setScreen(editorScreen)
      }
   }
}
