package me.collective.client.render

import me.collective.client.utils.inventory.TradeDefinition

@JvmRecord
data class TradePanelRequest(
    val trade: TradeDefinition?,
    val mode: TradePanelMode
) {
    companion object {
        @JvmStatic
        fun forCreate(): TradePanelRequest {
            return TradePanelRequest(null, TradePanelMode.CREATE)
        }

        @JvmStatic
        fun forEdit(selectedTrade: TradeDefinition): TradePanelRequest {
            return TradePanelRequest(selectedTrade, TradePanelMode.EDIT)
        }
    }
}
