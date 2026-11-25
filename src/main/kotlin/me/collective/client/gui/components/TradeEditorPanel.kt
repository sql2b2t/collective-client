package me.collective.client.gui.components

import com.google.common.base.Predicates
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.screens.TradeEditorScreen
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.TradeDefinition
import me.collective.client.utils.inventory.TradeEnchantmentEntry
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper
import java.util.ArrayList
import java.util.Objects
import java.util.Optional

class TradeEditorPanel(
   val screen: TradeEditorScreen,
   trade: TradeDefinition,
   actionLabel: String
) : Panel("Trade Editor"), ClientAccess {

   private val maxPriceSetting: NumberSetting<Int> = NumberSetting("MaxPrice", 32, 1, 64)
   private val clearEnchantsSetting: BooleanSetting
   private val tradeItems: TradeItemsComponent
   private val tradeName: String = trade.name()

   init {
      this.maxPriceSetting.value = trade.maxPrice()
      this.clearEnchantsSetting = BooleanSetting(
         "ClearEnchants",
         true
      ) { this.hasAnyEnchantedStack() }
      this.tradeItems = TradeItemsComponent(this, trade)
      this.children.add(this.tradeItems)
      this.children.add(
         NumericSettingComponent(
            this,
            CloseState.ALWAYS_OPEN,
            this.maxPriceSetting
         )
      )
      this.children.add(
         BooleanSettingComponent(
            this,
            CloseState.ALWAYS_OPEN,
            this.clearEnchantsSetting
         )
      )
      this.children.add(ActionButtonComponent(this, actionLabel, Runnable { this.saveTrade() }))
      this.clearEnchantsSetting.setChangeCallback { this.clearEnchantments() }
      this.x = (ClientAccess.collectiveMinecraft.window.guiScaledWidth - this.width) / 2
      this.y = 64
      this.disableHeightLimit()
   }

   override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) {
      super.mouseScrolled(mouseX, mouseY, amount)
      Objects.requireNonNull(this.screen)
   }

   private fun saveTrade() {
      val enchantments = ArrayList<TradeEnchantmentEntry>()
      val maxPrice = this.maxPriceSetting.value
      this.firstEnchantedStack.ifPresent { enchantedStack ->
         val levels: Map<ResourceKey<Enchantment>, Int> = EnchantmentUtils.getLevels(enchantedStack)
         levels.forEach { (enchantmentKey, level) ->
            enchantments.add(
               TradeEnchantmentEntry(enchantmentKey.location().path, level)
            )
         }
      }
      val trade = TradeDefinition(
         this.tradeName,
         this.tradeItems.fromStack.item,
         this.tradeItems.toStack.item,
         maxPrice,
         enchantments
      )
      ClientContext.tradeRegistry.reregisterByName(this.tradeName)
      ClientContext.tradeRegistry.registerTrade(trade)
      (ClientAccess.collectiveMinecraft.screen as? ClientScreen)?.closeWithTransition()
   }

   private fun hasAnyEnchantedStack(): Boolean {
      return hasEnchantments(this.tradeItems.fromStack) || hasEnchantments(this.tradeItems.toStack)
   }

   private fun clearEnchantments() {
      if (!this.clearEnchantsSetting.value) {
         EnchantmentHelper.updateEnchantments(
            this.tradeItems.fromStack
         ) { mutableEnchantments -> mutableEnchantments.removeIf(Predicates.alwaysTrue()) }
         EnchantmentHelper.updateEnchantments(
            this.tradeItems.toStack
         ) { mutableEnchantments -> mutableEnchantments.removeIf(Predicates.alwaysTrue()) }
         this.clearEnchantsSetting.value = true
      }
   }

   private val firstEnchantedStack: Optional<ItemStack>
      get() = if (hasEnchantments(this.tradeItems.fromStack)) {
         Optional.of(this.tradeItems.fromStack)
      } else if (hasEnchantments(this.tradeItems.toStack)) {
         Optional.of(this.tradeItems.toStack)
      } else {
         Optional.empty()
      }

   companion object {
      private fun hasEnchantments(stack: ItemStack): Boolean {
         return stack.isEnchanted || stack.has(DataComponents.STORED_ENCHANTMENTS)
      }
   }
}
