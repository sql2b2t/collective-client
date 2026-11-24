package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.components.CreateTradePanel
import me.collective.client.gui.components.TradeActionsPanel
import me.collective.client.gui.components.TradeListPanel
import me.collective.client.gui.panels.Panel
import me.collective.client.render.TradePanelMode
import me.collective.client.render.TradePanelRequest
import net.minecraft.client.gui.GuiGraphics

class TradeManagerScreen : ClientScreen() {
    private val tradeListPanel: TradeListPanel = TradeListPanel(this)
    private var actionPanel: Panel = CreateTradePanel(this)
    private var pendingPanelRequest: TradePanelRequest? = null

    init {
        this.tradeListPanel.updateLayout()
        this.panels.add(this.tradeListPanel)
        this.panels.add(this.actionPanel)
    }

    override fun renderContents(graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val request = this.pendingPanelRequest
        if (request != null) {
            this.tradeListPanel.refreshTrades()
            this.panels.remove(this.actionPanel)
            this.actionPanel = if (request.mode() == TradePanelMode.CREATE) {
                CreateTradePanel(this)
            } else {
                TradeActionsPanel(this, request.trade())
            }
            this.panels.add(this.actionPanel)
            this.pendingPanelRequest = null
        }
    }

    fun requestPanelChange(request: TradePanelRequest) {
        this.pendingPanelRequest = request
    }
}
