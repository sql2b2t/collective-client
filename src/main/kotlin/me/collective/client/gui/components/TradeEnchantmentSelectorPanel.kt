package me.collective.client.gui.components

import me.collective.client.render.TextUtils
import me.collective.client.utils.inventory.EnchantmentUtils
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import java.util.function.Predicate

class TradeEnchantmentSelectorPanel(tradeItems: TradeItemsComponent, fromSide: Boolean) :
   TradeSelectorPanel<EnchantmentListEntry>(tradeItems, fromSide) {

   private val enchantmentList: EnchantmentListComponent

   init {
      this.enchantmentList = EnchantmentListComponent(
         this,
         Predicate { this.matchesEnchantment(it) }
      )
      this.children.add(this.enchantmentList)
   }

   @Suppress("UNCHECKED_CAST")
   override fun applySelection(entry: EnchantmentListEntry) {
      val stack: ItemStack = if (this.isFromSide) {
         this.tradeItems.fromStack
      } else {
         this.tradeItems.toStack
      }
      val holder = EnchantmentUtils.getHolder(entry.enchantmentKey as ResourceKey<Enchantment>)
      if (holder != null) {
         stack.enchant(holder, entry.level)
      }
   }

   override fun getSelection(): EnchantmentListEntry? {
      return this.enchantmentList.selectedEntry
   }

   override fun getWidth(): Int {
      return super.getWidth() + 48
   }

   @Suppress("UNCHECKED_CAST")
   private fun matchesEnchantment(entry: EnchantmentListEntry): Boolean {
      val stack: ItemStack = if (this.isFromSide) {
         this.tradeItems.fromStack
      } else {
         this.tradeItems.toStack
      }
      val holder = EnchantmentUtils.getHolder(entry.enchantmentKey as ResourceKey<Enchantment>)
      var supported = (holder?.value() as? Enchantment)?.isSupportedItem(stack) == true
      if (stack.`is`(Items.ENCHANTED_BOOK) || stack.`is`(Items.BOOK)) {
         supported = true
      }

      return supported && TextUtils.containsIgnoreCase(entry.name, this.searchInput.text)
   }
}
