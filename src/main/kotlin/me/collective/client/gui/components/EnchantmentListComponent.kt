package me.collective.client.gui.components

import me.collective.client.ClientAccess
import me.collective.client.gui.panels.Panel
import me.collective.client.utils.inventory.EnchantmentUtils
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import java.util.function.Predicate
import java.util.regex.Matcher

class EnchantmentListComponent(
   panel: Panel,
   filter: Predicate<EnchantmentListEntry>
) : AbstractItemListComponent<EnchantmentListEntry>(panel, filter, 256) {

   override fun update(mouseX: Double, mouseY: Double) {
      this.entries.clear()
      val level = ClientAccess.collectiveMinecraft.level ?: return
      val registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)

      for (enchantmentKey in registry.registryKeySet()) {
         val enchantmentId = enchantmentKey.location()
         val enchantment = registry.getValue(enchantmentId) ?: continue
         val maxLevel = enchantment.maxLevel

         for (lvl in 1..maxLevel) {
            this.entries.add(EnchantmentListEntry(enchantmentKey, lvl, enchantment.description().string))
         }
      }

      this.entries.removeIf(this.filter.negate())
   }

   @Suppress("UNCHECKED_CAST")
   override fun getEntryLabel(entry: EnchantmentListEntry): String {
      val levelName = Component.translatable("enchantment.level." + entry.level).string
      val holder = EnchantmentUtils.getHolder(entry.enchantmentKey as ResourceKey<Enchantment>)
      val enchantment = holder?.value() ?: return entry.name
      if (enchantment.maxLevel == 1) {
         return entry.name
      }

      return (entry.name + " \u0001").replaceFirst("\u0001", Matcher.quoteReplacement(levelName))
   }

   override fun getEntryIcon(entry: EnchantmentListEntry): ItemStack {
      return ENCHANTED_BOOK_ICON
   }

   companion object {
      val ENCHANTED_BOOK_ICON: ItemStack = Items.ENCHANTED_BOOK.defaultInstance.apply {
         EnchantmentUtils.getHolder(Enchantments.FORTUNE)?.let { holder ->
            enchant(holder, 1337)
         }
      }
   }
}
