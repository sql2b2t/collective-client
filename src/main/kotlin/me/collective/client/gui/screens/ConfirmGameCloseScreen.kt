package me.collective.client.gui.screens

import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import me.collective.client.ClientAccess
import me.collective.mixin.MinecraftAccessor
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ConfirmGameCloseScreen : ConfirmScreen(
    CloseConfirmationHandler(),
    Component.literal("Confirm Game Close"),
    Component.literal("Are you sure that you want to close minecraft?")
) {
    private class CloseConfirmationHandler : BooleanConsumer, ClientAccess {
        override fun accept(confirmed: Boolean) {
            val client = ClientAccess.collectiveMinecraft
            if (!(client as MinecraftAccessor).`collective$isDisconnecting`()) {
                client.setScreen(null)
            }

            if (confirmed) {
                client.destroy()
            } else {
                val windowHandle = client.window.window
                GLFW.glfwSetWindowShouldClose(windowHandle, false)
            }
        }
    }
}
