package me.collective.client.gui.screens

import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import me.collective.client.ClientAccess
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component

class DisconnectConfirmationScreen(confirmationAction: Runnable) : ConfirmScreen(
    ConfirmationCallback(confirmationAction),
    Component.literal("Confirm Disconnect"),
    Component.literal("Are you sure that you want to disconnect?")
) {
    @JvmRecord
    private data class ConfirmationCallback(val confirmationAction: Runnable) : BooleanConsumer, ClientAccess {
        override fun accept(confirmed: Boolean) {
            ClientAccess.collectiveMinecraft.gui.setScreen(null)
            if (confirmed) {
                this.confirmationAction.run()
            }
        }
    }
}
