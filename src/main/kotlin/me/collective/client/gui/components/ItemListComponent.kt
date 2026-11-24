package me.collective.client.gui.components

import me.collective.client.gui.panels.Panel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

class ItemListComponent(
   panel: Panel,
   filter: Predicate<Item>
) : AbstractItemListComponent<Item>(panel, filter, BuiltInRegistries.ITEM.size()) {

   override fun update(mouseX: Double, mouseY: Double) {
      this.entries.clear()
      BuiltInRegistries.ITEM
         .stream()
         .filter(this.filter)
         .forEach { this.entries.add(it) }
   }

   override fun getEntryLabel(entry: Item): String {
      return entry.name.string
   }

   override fun getEntryIcon(entry: Item): ItemStack {
      return entry.defaultInstance
   }
}
