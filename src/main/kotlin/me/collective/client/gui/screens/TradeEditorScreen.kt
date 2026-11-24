package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.components.TradeEditorPanel
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.inventory.TradeDefinition
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

class TradeEditorScreen : ClientScreen {
    private val editorPanel: TradeEditorPanel
    var tooltipStack: ItemStack? = null

    constructor(tradeName: String) {
        val trade = ClientContext.tradeRegistry.createDefault(tradeName)
        this.editorPanel = TradeEditorPanel(this, trade, "Create")
        this.panels.add(this.editorPanel)
    }

    constructor(trade: TradeDefinition) {
        this.editorPanel = TradeEditorPanel(this, trade, "Edit")
        this.panels.add(this.editorPanel)
    }

    override fun renderContents(graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        GuiDrawUtils.flush()
        val currentTooltip = this.tooltipStack
        if (currentTooltip != null) {
            graphics.setTooltipForNextFrame(
                ClientAccess.collectiveMinecraft.font,
                currentTooltip,
                mouseX,
                mouseY
            )
        }
        this.tooltipStack = null
    }
}
