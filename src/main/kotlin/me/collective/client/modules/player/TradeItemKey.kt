package me.collective.client.modules.player

import me.collective.client.utils.inventory.TradeOfferSnapshot

import net.minecraft.world.item.Item
import net.minecraft.world.item.trading.MerchantOffer

data class TradeItemKey(
   val costItem: Item,
   val resultItem: Item
) {

   companion object {
      @JvmStatic
      fun fromOffer(offer: MerchantOffer): TradeItemKey {
         val costItem: Item = offer.getCostA().item
         val resultItem: Item = offer.getResult().item
         return TradeItemKey(costItem, resultItem)
      }

      @JvmStatic
      fun fromCachedOffer(offer: TradeOfferSnapshot): TradeItemKey {
         return TradeItemKey(offer.firstCost!!.item, offer.result!!.item)
      }
   }

}
