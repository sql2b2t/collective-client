package me.collective.client.modules.render

import me.collective.client.ClientAccess

import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import net.minecraft.client.player.LocalPlayer

class NoBobModule : Module("NoBob", "Modifies the bobbing animation.", ModuleCategory.RENDER) {
    @JvmField
    val multiplierSetting: Setting<Float> = add(NumberSetting("Multiplier", 0.0F, 0.0F, 1.0F))

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        if (this.isWorldUnavailable()) return

        val multiplier: Float = this.multiplierSetting.value!!
        if (multiplier <= 0.0F) {
            val player: LocalPlayer? = ClientAccess.collectiveMinecraft.player!!
            player!!.bob = 0.0F
        }
    }
}
