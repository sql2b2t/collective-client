package me.collective.client.utils.inventory

import java.util.Optional
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer

data class TradeOfferSnapshot(
   val firstCost: ItemStack?,
   val result: ItemStack?,
   val secondCost: Optional<*>
) {
   fun firstCost(): ItemStack? {
      return this.firstCost
   }

   fun result(): ItemStack? {
      return this.result
   }

   fun secondCost(): Optional<*> {
      return this.secondCost
   }

   companion object {
      @JvmField
      val OUT_OF_STOCK: TradeOfferSnapshot = TradeOfferSnapshot(null, null, Optional.empty<Any>())

      @JvmStatic
      fun fromOffer(offer: MerchantOffer): TradeOfferSnapshot {
         val firstCost = offer.getCostA().copy()
         return TradeOfferSnapshot(firstCost, offer.assemble(), offer.getItemCostB())
      }
   }
}
