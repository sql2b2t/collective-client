package me.collective.client.access

import me.collective.client.ClientAccess
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import java.util.ArrayList
import java.util.Optional

object TooltipComponentCapture : ClientAccess {
    @JvmStatic
    var isCaptureEnabled: Boolean = false
        private set

    @JvmStatic
    var capturedComponents: MutableList<Any?>? = null

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun captureComponents(
        graphics: GuiGraphics,
        text: List<*>,
        tooltipComponent: Optional<*>
    ): MutableList<Any?> {
        isCaptureEnabled = true
        val font: Font = ClientAccess.collectiveMinecraft.font
        graphics.renderTooltip(
            font,
            text as List<net.minecraft.network.chat.Component>,
            tooltipComponent as Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>,
            0,
            0
        )
        isCaptureEnabled = false
        val captured = capturedComponents
        return if (captured == null) ArrayList() else ArrayList(captured)
    }

    @JvmStatic
    fun setCaptureEnabled(enabled: Boolean) {
        isCaptureEnabled = enabled
    }
}
