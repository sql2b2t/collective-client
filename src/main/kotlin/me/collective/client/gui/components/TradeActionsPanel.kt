package me.collective.client.gui.components

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.TradeEditorScreen
import me.collective.client.gui.screens.TradeManagerScreen
import me.collective.client.render.TradePanelRequest
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.TradeDefinition
import java.util.function.Predicate

class TradeActionsPanel(
   private val managerScreen: TradeManagerScreen,
   private val trade: TradeDefinition
) : Panel("Trade"), ClientAccess {
   private val deleteConfirmationTimer = ElapsedTimer()

   init {
      this.x = this.x + this.width + 4
      this.registerChild(ButtonComponent(this, "Trade: " + trade.name()))
      this.registerChild(ActionButtonComponent(this, "Edit", Runnable { this.openEditor() }))
      this.registerChild(
         ActionButtonComponent(
            this,
            { this.deleteButtonLabel },
            Runnable { this.deleteIfConfirmed() }
         )
      )
      this.registerChild(ActionButtonComponent(this, "Cancel", Runnable { this.showCreatePanel() }))
   }

   private fun openEditor() {
      val registeredTrade = ClientContext.tradeRegistry
         .findFirst(Predicate { candidate: TradeDefinition -> candidate.name() == this.trade.name() })
         .orElse(null) as? TradeDefinition
      val editorScreen = TradeEditorScreen(registeredTrade ?: this.trade)
      editorScreen.parentScreen = this.managerScreen
      ClientAccess.collectiveMinecraft.gui.setScreen(editorScreen)
   }

   private fun deleteIfConfirmed() {
      if (!this.deleteConfirmationTimer.hasElapsedAndReset(1000L)) {
         ClientContext.tradeRegistry.reregisterByName(this.trade.name())
         this.showCreatePanel()
      }
   }

   private fun showCreatePanel() {
      this.managerScreen.requestPanelChange(TradePanelRequest.forCreate())
   }

   private val deleteButtonLabel: String
      get() = if (this.deleteConfirmationTimer.hasElapsed(1000L)) "Delete" else "Are you sure?"
}
