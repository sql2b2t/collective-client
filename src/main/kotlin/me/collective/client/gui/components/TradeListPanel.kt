package me.collective.client.gui.components

import me.collective.client.ClientContext
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.TradeManagerScreen
import me.collective.client.render.TradePanelRequest
import me.collective.client.utils.inventory.TradeDefinition

class TradeListPanel(private val managerScreen: TradeManagerScreen) : Panel("List") {

   init {
      this.refreshTrades()
   }

   override fun init() {
      super.init()
      this.refreshTrades()
   }

   fun refreshTrades() {
      this.children.clear()

      for (registeredEntry in ClientContext.tradeRegistry.getRegistry()) {
         val registeredTrade = registeredEntry as TradeDefinition
         this.registerChild(
            ActionButtonComponent(this, registeredTrade.name(), Runnable { this.selectTrade(registeredTrade) })
         )
      }

      this.updateLayout()
   }

   private fun selectTrade(selectedTrade: TradeDefinition) {
      this.managerScreen.requestPanelChange(TradePanelRequest.forEdit(selectedTrade))
   }
}
