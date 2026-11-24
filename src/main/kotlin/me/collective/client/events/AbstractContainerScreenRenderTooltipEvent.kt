package me.collective.client.events

import net.minecraft.client.gui.screens.Screen

class AbstractContainerScreenRenderTooltipEvent(
    val screen: Screen,
    var tooltipComponents: List<*>?,
    var x: Int,
    var y: Int,
    currentMouseX: Int,
    currentMouseY: Int,
) : CancellableEvent() {
    init {
        mouseX = currentMouseX
        mouseY = currentMouseY
    }

    companion object {
        // Java reads these statically: AbstractContainerScreenRenderTooltipEvent.mouseX
        @JvmField
        var mouseX: Int = 0

        @JvmField
        var mouseY: Int = 0
    }
}
