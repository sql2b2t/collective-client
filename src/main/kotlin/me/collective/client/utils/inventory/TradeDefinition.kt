package me.collective.client.utils.inventory

import me.collective.client.settings.NamedEntry

import com.google.gson.annotations.SerializedName
import java.util.Objects
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment

class TradeDefinition(
   @SerializedName("name")
   private val storedName: String,
   @SerializedName("from")
   private val fromItem: Item,
   @SerializedName("to")
   private val toItem: Item,
   @SerializedName("maxPrice")
   private val maxPriceValue: Int,
   @SerializedName("enchants")
   private val enchantsList: List<TradeEnchantmentEntry>
) : NamedEntry {

   fun getStoredName(): String {
      return this.storedName
   }

   fun getFromItem(): Item {
      return this.fromItem
   }

   fun getToItem(): Item {
      return this.toItem
   }

   fun getMaxPrice(): Int {
      return this.maxPriceValue
   }

   fun getEnchants(): List<TradeEnchantmentEntry> {
      return this.enchantsList
   }

   override fun getName(): String {
      return this.storedName
   }

   fun matches(offer: TradeOfferSnapshot): Boolean {
      val matchedStack = offer.firstCost() ?: return false
      val fromItem = this.fromItem
      if (matchedStack.`is`(fromItem)) {
         val resultStack = offer.result() ?: return false
         val toItem = this.toItem
         if (resultStack.`is`(toItem)) {
            val firstCost = offer.firstCost() ?: return false
            if (firstCost.count > this.maxPriceValue) {
               return false
            }

            val enchantmentsIterator = this.enchantsList.iterator()

            while (enchantmentsIterator.hasNext()) {
               val enchantment = enchantmentsIterator.next() as TradeEnchantmentEntry
               val enchantmentName = enchantment.name()
               val enchantmentKey: ResourceKey<Enchantment> = EnchantmentUtils.getKey(enchantmentName)
               val enchantedResult = offer.result() ?: return false
               val actualLevel = EnchantmentUtils.getLevel(enchantmentKey, enchantedResult)
               if (actualLevel > 0) {
                  if (enchantment.level() < 0) {
                     continue
                  }
               }

               if (actualLevel != enchantment.level()) {
                  return false
               }
            }

            return true
         }
      }

      return false
   }

   override fun toString(): String {
      return "TradeDefinition[name=" + this.storedName +
         ", from=" + this.fromItem + ", to=" + this.toItem + ", maxPrice=" + this.maxPriceValue + ", enchants=" + this.enchantsList + "]"
   }

   override fun hashCode(): Int {
      var hash = Objects.hashCode(this.storedName)
      hash = 31 * hash + Objects.hashCode(this.fromItem)
      hash = 31 * hash + Objects.hashCode(this.toItem)
      hash = 31 * hash + this.maxPriceValue
      return 31 * hash + Objects.hashCode(this.enchantsList)
   }

   override fun equals(other: Any?): Boolean {
      if (this === other) {
         return true
      }
      if (other !is TradeDefinition) {
         return false
      }

      return Objects.equals(this.enchantsList, other.enchantsList) &&
         this.maxPriceValue == other.maxPriceValue &&
         Objects.equals(this.toItem, other.toItem) &&
         Objects.equals(this.fromItem, other.fromItem) &&
         Objects.equals(this.storedName, other.storedName)
   }

   @SerializedName("name")
   fun name(): String {
      return this.storedName
   }

   @SerializedName("from")
   fun from(): Item {
      return this.fromItem
   }

   @SerializedName("to")
   fun to(): Item {
      return this.toItem
   }

   @SerializedName("maxPrice")
   fun maxPrice(): Int {
      return this.maxPriceValue
   }

   @SerializedName("enchants")
   fun enchants(): List<TradeEnchantmentEntry> {
      return this.enchantsList
   }

}
