package me.collective.client.utils.inventory

import net.minecraft.core.component.DataComponentHolder
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents

data class ShulkerBoxItemPreview(
   @get:JvmName("containerStack") val containerStack: ItemStack,
   @get:JvmName("previewStack") val previewStack: ItemStack?
) {
   companion object {
      @JvmStatic
      fun create(stack: ItemStack): ShulkerBoxItemPreview? {
         val containerItem = stack.getItem()

         if (!ItemUtils.isShulkerBox(containerItem)) {
            return null
         }

         val containerType: DataComponentType<ItemContainerContents> = DataComponents.CONTAINER
         val contents = (stack as DataComponentHolder).get(containerType)
         if (contents == null) {
            return null
         }

         val occupiedSlotCounts: MutableMap<Item, Int> = HashMap()
         val stacksIterator = contents.stream().toList().iterator()

         while (stacksIterator.hasNext()) {
            val containedStack = stacksIterator.next()
            if (!containedStack.isEmpty()) {
               val containedItem = containedStack.getItem()
               val previousSlotCount = occupiedSlotCounts.getOrDefault(containedItem, 0)
               val nextSlotCount = 1 + previousSlotCount
               occupiedSlotCounts[containedItem] = nextSlotCount
            }
         }

         if (occupiedSlotCounts.isEmpty()) {
            return null
         }

         val selectedEntry = occupiedSlotCounts.entries.stream()
            .max(java.util.Map.Entry.comparingByValue<Item, Int>())
            .orElse(null)
         if (selectedEntry == null) {
            return null
         }

         return ShulkerBoxItemPreview(stack, selectedEntry.key.getDefaultInstance())
      }
   }
}
