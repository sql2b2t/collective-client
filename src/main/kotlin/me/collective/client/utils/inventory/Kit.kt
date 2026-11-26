package me.collective.client.utils.inventory

import me.collective.client.managers.KitRegistry

import me.collective.client.settings.NamedEntry

import java.util.Objects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class Kit(
   @get:JvmName("kitName")
   val name: String
) : NamedEntry {
   val itemsBySlot: MutableMap<Int, ItemDescriptor> = HashMap()

   override fun getName(): String = this.name

   data class ItemDescriptor(
      val itemId: Int,
      val potionSignature: String?
   ) {
      fun itemId(): Int {
         return this.itemId
      }

      fun potionSignature(): String? {
         return this.potionSignature
      }

      fun matches(stack: ItemStack): Boolean {
         val item = stack.getItem()
         if (Item.getId(item) == this.itemId) {

            if (Objects.equals(
                  KitRegistry.getPotionSignature(stack),
                  this.potionSignature
               )
            ) {
               return true
            }
         }

         return false
      }
   }
}
