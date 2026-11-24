package me.collective.client.events

import me.collective.client.render.GuiProjectionUtils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

class AbstractContainerScreenEvent(screen: Screen) : CancellableEvent() {
    val screen: Screen = screen

    var poseStack: PoseStack? = null
        private set

    var graphics: GuiGraphics? = null
        set(value) {
            field = value
            poseStack = if (value != null) GuiProjectionUtils.toPoseStack(value) else null
        }

    var mouseX: Float = 0.0f

    var mouseY: Float = 0.0f

    var tickCallback: Runnable? = null

    fun runTickCallback() {
        this.tickCallback?.run()
    }
}
