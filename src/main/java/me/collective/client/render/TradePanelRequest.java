package me.collective.client.render;

import me.collective.client.utils.inventory.TradeDefinition;

public record TradePanelRequest(
   TradeDefinition trade,
   TradePanelMode mode
) {
   public static TradePanelRequest forCreate() {
      return new TradePanelRequest(null, TradePanelMode.CREATE);
   }

   public static TradePanelRequest forEdit(
      TradeDefinition selectedTrade
   ) {
      return new TradePanelRequest(selectedTrade, TradePanelMode.EDIT);
   }

}
