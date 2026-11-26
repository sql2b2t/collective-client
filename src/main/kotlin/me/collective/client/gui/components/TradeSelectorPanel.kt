package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.settings.StringSetting

abstract class TradeSelectorPanel<T>(
   protected val tradeItems: TradeItemsComponent,
   val isFromSide: Boolean
) : Panel(if (isFromSide) "From" else "To"), ClientAccess {
   protected val searchInput: StringSettingComponent

   init {
      val searchSetting = StringSetting("Search", "")
      this.searchInput = StringSettingComponent(
         this, CloseState.ALWAYS_OPEN, searchSetting
      )
      this.children.add(this.searchInput)
      this.disableHeightLimit()
   }

   protected abstract fun applySelection(selection: T)

   protected abstract fun getSelection(): T?

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)
      val screen = ClientAccess.collectiveMinecraft.gui.screen() as? ClientScreen
      if (!this.tradeItems.isSelectorOpen && screen != null) {
         ClientContext.taskScheduler.scheduleRotationTask(
            Runnable { screen.panels.remove(this) },
            0
         )
      }
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      super.mouseClicked(mouseX, mouseY, button)
      val selection = this.getSelection()
      if (selection != null) {
         val screen = ClientAccess.collectiveMinecraft.gui.screen() as? ClientScreen
         if (screen != null) {
            ClientContext.taskScheduler.scheduleRotationTask(
               Runnable {
                  screen.panels.remove(this)
                  this.applySelection(selection)
                  this.tradeItems.closeSelector()
               },
               0
            )
         }
      }
   }
}
