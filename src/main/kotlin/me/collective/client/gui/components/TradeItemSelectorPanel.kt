package me.collective.client.gui.components

import me.collective.client.ClientAccess
import me.collective.client.render.TextUtils
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.function.Predicate

class TradeItemSelectorPanel(tradeItems: TradeItemsComponent, fromSide: Boolean) :
   TradeSelectorPanel<Item>(tradeItems, fromSide), ClientAccess {

   private val itemList: ItemListComponent

   init {
      this.itemList = ItemListComponent(
         this,
         Predicate { item ->
            TextUtils.containsIgnoreCase(
               item.name.string,
               this.searchInput.text
            )
         }
      )
      this.children.add(this.itemList)
   }

   override fun applySelection(item: Item) {
      if (this.isFromSide) {
         this.tradeItems.toStack = Items.EMERALD.defaultInstance
         this.tradeItems.fromStack = item.defaultInstance
      } else {
         this.tradeItems.fromStack = Items.EMERALD.defaultInstance
         this.tradeItems.toStack = item.defaultInstance
      }
   }

   override fun getSelection(): Item? {
      return this.itemList.selectedEntry
   }
}
