package me.collective.client.modules.player

import me.collective.client.utils.inventory.TradeOfferSnapshot

import java.util.ArrayList
import java.util.HashMap
import net.minecraft.world.entity.npc.villager.AbstractVillager
import net.minecraft.world.item.trading.MerchantOffer

class VillagerOfferCache {
   private val entries: MutableMap<Int, Entry> = HashMap()

   @Synchronized
   fun getLastUpdatedMillis(entityId: Int): Long {
      val entry = this.entries[entityId]
      return entry?.lastUpdatedMillis ?: 0L
   }

   @Synchronized
   fun updateOffers(villager: AbstractVillager?, offers: Iterable<MerchantOffer>) {
      if (villager != null) {
         val capturedEntries = this.entries
         val entityId = villager.id
         val entry = capturedEntries.getOrPut(entityId) { Entry() }
         entry.lastUpdatedMillis = System.currentTimeMillis()
         entry.offers.clear()
         val offerIterator = offers.iterator()

         while (offerIterator.hasNext()) {
            val offer = offerIterator.next()
            if (offer.isOutOfStock) {
               entry.offers.add(TradeOfferSnapshot.OUT_OF_STOCK)
            } else {
               val snapshot = TradeOfferSnapshot.fromOffer(offer)
               entry.offers.add(snapshot)
            }
         }
      }
   }

   @Synchronized
   fun getOffers(entityId: Int): List<TradeOfferSnapshot>? {
      val entry = this.entries[entityId] ?: return null
      // Копия: обновление идёт из netty-потока, живой список рвался бы у читателя (CME).
      return entry.offers.toList()
   }

   private class Entry {
      val offers: MutableList<TradeOfferSnapshot>

      var lastUpdatedMillis: Long = 0

      init {
         this.offers = ArrayList(10)
      }

   }
}
